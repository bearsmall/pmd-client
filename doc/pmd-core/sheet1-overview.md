# 工具入口类
* net.sourceforge.pmd.PMD
> args 接收命令行参数最后借助JCommander工具将其转换为PMDConfiguration（运行时配置项），最后实际调用PMD类的doPMD方法

```java
public final class PMDCommandLineInterface {
        public static void run(String[] args) {
            setStatusCodeOrExit(PMD.run(args));
        }
}
```
```java
public class PMD{

	public static void main(String[] args) {
		   PMDCommandLineInterface.run(args);
	}
	
	//...
	
	public static int doPMD(PMDConfiguration configuration) {
	    //...
	    processFiles(configuration, ruleSetFactory, files, ctx, renderers);
	    //...
	}
	
	public static void processFiles(final PMDConfiguration configuration, final RuleSetFactory ruleSetFactory,
				final List<DataSource> files, final RuleContext ctx, final List<Renderer> renderers){
		//...
		sortFiles(configuration, files);
		//... 
		if (configuration.getThreads() > 0) {//多线程模式
			new MultiThreadProcessor(configuration).processFiles(silentFactoy, files, ctx, renderers);
		} else {
			new MonoThreadProcessor(configuration).processFiles(silentFactoy, files, ctx, renderers);
		}
		//...
	} 
}
```

* net.sourceforge.pmd.processor.AbstractPMDProcessor.processFiles
>MultiThreadProcessor和MonoThreadProcessor的父类是AbstractPMDProcessor。processFiles方法具体分三步：
>1. runAnalysis方法负责分析每个源文件
>2. renderReports负责将检测执行过程中出现的通用性错误（error）暴露出去
>3. collectReports方法负责将每个源文件的检测结果（violation）逐一采集并暴露出去

```java
public class AbstractPMDProcessor {
    
    //...
        public void processFiles(RuleSetFactory ruleSetFactory, List<DataSource> files, RuleContext ctx,List<Renderer> renderers) {
    	    
            RuleSets rs = createRuleSets(ruleSetFactory, ctx.getReport());
            configuration.getAnalysisCache().checkValidity(rs, configuration.getClassLoader());
            SourceCodeProcessor processor = new SourceCodeProcessor(configuration);
    
            for (DataSource dataSource : files) {
                String niceFileName = filenameFrom(dataSource);
    
                runAnalysis(new PmdRunnable(dataSource, niceFileName, renderers, ctx, rs, processor));
            }
    
            // render base report first - general errors
            renderReports(renderers, ctx.getReport());
            
            // then add analysis results per file
            collectReports(renderers);
        }
        
    //...
        
}
```

* net.sourceforge.pmd.processor.MultiThreadProcessor.runAnalysis
> runAnalysis方法由实现类具体完成（如这里的多线程实现方式）【看这个文件的git历史记录，可以发现pmd多线程处理模型不断优化的过程】，这里我把整个类贴出来。

```java
public class MultiThreadProcessor extends AbstractPMDProcessor {
    private final ExecutorService executor;
    private final CompletionService<Report> completionService;

    private long submittedTasks = 0L;

    public MultiThreadProcessor(final PMDConfiguration configuration) {
        super(configuration);

        executor = Executors.newFixedThreadPool(configuration.getThreads(), new PmdThreadFactory());
        completionService = new ExecutorCompletionService<>(executor);
    }

    @Override
    protected void runAnalysis(PmdRunnable runnable) {
        completionService.submit(runnable);
        submittedTasks++;
    }

    @Override
    protected void collectReports(List<Renderer> renderers) {
        try {
            for (int i = 0; i < submittedTasks; i++) {
                final Report report = completionService.take().get();
                super.renderReports(renderers, report);//这里选择将检测到的结果送给renders进行渲染，如XMLRender就是将结果转为xml格式输出的render【出于非命令行输出的另外的目的我们可以实现自己的render或者直接收集检测结果集】
            }
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException ee) {
            final Throwable t = ee.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new IllegalStateException("PmdRunnable exception", t);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

```
* net.sourceforge.pmd.processor.MonoThreadProcessor.runAnalysis
>下面是单线程模型的
```java
public final class MonoThreadProcessor extends AbstractPMDProcessor {

    private final List<Report> reports = new ArrayList<>();
    
    public MonoThreadProcessor(PMDConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected void runAnalysis(PmdRunnable runnable) {
        // single thread execution, run analysis on same thread
        reports.add(runnable.call());
    }

    @Override
    protected void collectReports(List<Renderer> renderers) {
        for (Report r : reports) {
            super.renderReports(renderers, r);
        }

        // Since this thread may run PMD again, clean up the runnable
        PmdRunnable.reset();
    }
}
```
* net.sourceforge.pmd.processor.PmdRunnable.call
>不难发现，处理的核心是PmdRunnable类（对应Callable对象的call方法）
```java
public class PmdRunnable implements Callable<Report> {

	//...
    @Override
    public Report call() {
        TimeTracker.initThread();
        
        ThreadContext tc = LOCAL_THREAD_CONTEXT.get();
        if (tc == null) {
            tc = new ThreadContext(new RuleSets(ruleSets), new RuleContext(ruleContext));
            LOCAL_THREAD_CONTEXT.set(tc);
        }

        Report report = Report.createReport(tc.ruleContext, fileName);

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Processing " + tc.ruleContext.getSourceCodeFilename());
        }
        for (Renderer r : renderers) {
            r.startFileAnalysis(dataSource);
        }

        try (InputStream stream = new BufferedInputStream(dataSource.getInputStream())) {
            tc.ruleContext.setLanguageVersion(null);
            sourceCodeProcessor.processSourceCode(stream, tc.ruleSets, tc.ruleContext);
        } catch (PMDException pmde) {
            addError(report, pmde, "Error while processing file: " + fileName);
        } catch (IOException ioe) {
            addError(report, ioe, "IOException during processing of " + fileName);
        } catch (RuntimeException re) {
            addError(report, re, "RuntimeException during processing of " + fileName);
        }

        TimeTracker.finishThread();
        
        return report;
    }
	//...
}
```

* net.sourceforge.pmd.SourceCodeProcessor.processSourceCode
> Callable实际执行SourceCodeProcessor.processSourceCode(stream, tc.ruleSets, tc.ruleContext);

```java
public class SourceCodeProcessor {
    //...
    public void processSourceCode(InputStream sourceCode, RuleSets ruleSets, RuleContext ctx) throws PMDException {
        try (Reader streamReader = new InputStreamReader(sourceCode, configuration.getSourceEncoding())) {
            processSourceCode(streamReader, ruleSets, ctx);
        } catch (IOException e) {
            throw new PMDException("IO exception: " + e.getMessage(), e);
        }
    }
    //...
    public void processSourceCode(Reader sourceCode, RuleSets ruleSets, RuleContext ctx) throws PMDException{
    	//...
    	processSource(sourceCode, ruleSets, ctx);
    	//...
    } 
    
    //...    
    private void processSource(Reader sourceCode, RuleSets ruleSets, RuleContext ctx) {
		LanguageVersion languageVersion = ctx.getLanguageVersion();
		LanguageVersionHandler languageVersionHandler = languageVersion.getLanguageVersionHandler();
		Parser parser = PMD.parserFor(languageVersion, configuration);
	
		Node rootNode = parse(ctx, sourceCode, parser);
		
		//core below
		resolveQualifiedNames(rootNode, languageVersionHandler);
		symbolFacade(rootNode, languageVersionHandler);
		Language language = languageVersion.getLanguage();
		usesDFA(languageVersion, rootNode, ruleSets, language);
		usesTypeResolution(languageVersion, rootNode, ruleSets, language);
		usesMultifile(rootNode, languageVersionHandler, ruleSets, language);
		//core above
		List<Node> acus = Collections.singletonList(rootNode);
		ruleSets.apply(acus, ctx, language);
    }
    //...    
    private Node parse(RuleContext ctx, Reader sourceCode, Parser parser) {
		try (TimedOperation to = TimeTracker.startOperation(TimedOperationCategory.PARSER)) {
			Node rootNode = parser.parse(ctx.getSourceCodeFilename(), sourceCode);
			ctx.getReport().suppress(parser.getSuppressMap());
			return rootNode;
		}
	}
    //...
}
```