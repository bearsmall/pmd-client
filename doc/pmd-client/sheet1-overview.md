# PMD客户端

#### 从AbstractPMDProcessor.processFiles可以看到分析引擎执行的具体步骤如下：
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

#### 可以看到，PMD检测结果采集的关键时机在collectReport方法中（前一步的renderReports方法是为了采集系统错误等信息）
>我们设计一个新的Processor继承于AbstractPMDProcessor并重写其collectReports方法即可在最低代码侵入的程度下完成对PMD检测结果的“窃取”，如此这般我们就不必拘泥于PMD官方命令行中用法，可以将其嵌入其它如B/S架构的系统中。
>* 下面是仿造MultiThreadProcessor完成的截取最终检测结果集的检测处理器类：ResultProcessor的实现
>* 关键代码就两行：final Report report = completionService.take().get(); 以及 reports.add(report);

```java
public class ResultProcessor extends AbstractPMDProcessor {
    private final ExecutorService executor;
    private final CompletionService<Report> completionService;
    private List<Report> reports = new LinkedList<>();

    private long submittedTasks = 0L;

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    public ResultProcessor(final PMDConfiguration configuration) {
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
                reports.add(report);
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

#### 为了更方便使用，我们封装一个PMDClient客户端类，并设计一个简便的parse方法，parse方法的入参为PMDConfiguration（由调用用户根据实际情况构造）
>* 不难发现，我们在这里指定了一些默认参数，如语言为Java语言（PMD一般针对Java语言居多），如果需要使用其他语言模块也可继续封装
>* 获取待测源文件列表
>* 采用我们自定义的处理器（ResultProcessor）
>* renderList根据需要设定（attention：这里renderList如果等于null会出现空指针异常，所以即使不需要render，但至少传入一个size为0的List象吧）

```java
public class PMDClient {

    public static List<Report> parse(PMDConfiguration pmdConfiguration){
        RuleSetFactory ruleSetFactory = RulesetsFactoryUtils.getRulesetFactory(pmdConfiguration, new ResourceLoader());
        Set<Language> languages = new HashSet<>();
        languages.add(new JavaLanguageModule());
        List<DataSource> files = PMD.getApplicableFiles(pmdConfiguration, languages);
        final RuleSetFactory silentFactoy = new RuleSetFactory(ruleSetFactory, false);

        ResultProcessor resultProcessor = new ResultProcessor(pmdConfiguration);
        List<Renderer> rendererList = new ArrayList<>();
//        rendererList.add(new XMLRenderer());
        resultProcessor.processFiles(silentFactoy,files,new RuleContext(),rendererList);

        return resultProcessor.getReports();
    }
}

```

#### 看看客户端如何调用
>客户端针对PMDClient编程即可，下面是一个单元测试类，测试目的很明确
>* 构造PMDConfiguration对象（主要指定源文件path和规则集ruleSets）
>* 调用PMDClient.parse(pmdConfiguration)得到reports检测结果集（以单个源文件为单位）
>* 迭代打印输出

```java
public class PMDClientTest {

    @Test
    public void parse(){
        //step one. specify the PMDConfiguration（Not limited to src path and ruleSets）
        PMDConfiguration pmdConfiguration = new PMDConfiguration();
        pmdConfiguration.setRuleSets("rulesets/internal/all-java.xml");
        pmdConfiguration.setInputPaths(PMDClientTest.class.getClassLoader().getResource("src").getPath());

        //step two. let pmd start parsing and generate result reports
        List<Report> reports = PMDClient.parse(pmdConfiguration);

        //step three. show the check results
        for (Report report:reports){
            Iterator<RuleViolation> iterator = report.iterator();
            while (iterator.hasNext()){
                RuleViolation violation = iterator.next();
                System.out.println(violation.getFilename()+"->"+violation.getDescription());
            }
        }
    }
}
```
#### 输出
```text
十一月 25, 2018 4:43:43 下午 net.sourceforge.pmd.processor.AbstractPMDProcessor removeBrokenRules
警告: Removed misconfigured rule: LoosePackageCoupling  cause: No packages or classes specified
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Avoid short class names like Demo
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Header comments are required
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Field comments are required
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Found non-transient, non-static member. Please mark as transient or provide accessors.
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Field comments are required
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Found non-transient, non-static member. Please mark as transient or provide accessors.
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Field comments are required
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Avoid using redundant field initializer for 'submittedTasks'
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Field comments are required
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Found non-transient, non-static member. Please mark as transient or provide accessors.
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Public method and constructor comments are required
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Parameter 'runnable' is not assigned and could be declared final
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Parameter 'renderers' is not assigned and could be declared final
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Potential violation of Law of Demeter (method chain calls)
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Potential violation of Law of Demeter (method chain calls)
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Avoid variables with short names like t
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->New exception is thrown in catch block, original stack trace may be lost
D:\Users\bearsmall\IdeaProjects\pmd-client\target\classes\src\Demo.java->Parameter 'reports' is not assigned and could be declared final

Process finished with exit code 0
```