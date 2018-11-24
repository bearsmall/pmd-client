package com.xy.pmd.client;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleViolation;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;

public class PMDClientTest {

    @Test
    public void test1(){
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