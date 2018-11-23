package com.xy.pmd.client;

import com.xy.pmd.processor.ResultProcessor;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.util.ResourceLoader;
import net.sourceforge.pmd.util.datasource.DataSource;

import java.util.*;

public class PMDClient {

    public static List<Report> parse(PMDConfiguration pmdConfiguration){
        RuleSetFactory ruleSetFactory = RulesetsFactoryUtils.getRulesetFactory(pmdConfiguration, new ResourceLoader());
        Set<Language> languages = new HashSet<>();
        languages.add(new JavaLanguageModule());
        List<DataSource> files = PMD.getApplicableFiles(pmdConfiguration, languages);
        final RuleSetFactory silentFactoy = new RuleSetFactory(ruleSetFactory, false);

        ResultProcessor resultProcessor = new ResultProcessor(pmdConfiguration);
        resultProcessor.processFiles(silentFactoy,files,new RuleContext(),new ArrayList<>());

        return resultProcessor.getReports();
    }
}
