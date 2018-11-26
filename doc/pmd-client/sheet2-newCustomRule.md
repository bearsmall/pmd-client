# 编写自己的规则

### PMD支持两种方式编写自定义规则
>1. Write Code
>2. Write XPath

### 1. Define new custom rule through writting code

* first of all，define your rule config file
>1. each rule identified by name is under rulesets which contains a collectino of rules basically grouped by ruleset name
>2. each rule config unique name、message、class、description、priority、example ,etc。
>3. the class parameter refers to the implementation Class of this rule,which means com.xy.pmd.lang.java.rule.errorprone.XYDepricatedSingleMethodSingletonRule in this sample
>4. basically,you can extends AbstractJavaRule and override various of 'visit' methods to finish the rule implementation,as it suggests,it is AST which it relies on.
>5. use the PMDClient to test this new rule and have fun with it

```xml
<?xml version="1.0"?>

    <ruleset name="Error Prone"
             xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 http://pmd.sourceforge.net/ruleset_2_0_0.xsd">

        <description>
            Rules to detect constructs that are either broken, extremely confusing or prone to runtime errors.
        </description>

        <rule name="SingleMethodSingleton"
          since="5.4"
          message="Class contains multiple getInstance methods. Please review."
          class="com.xy.pmd.lang.java.rule.errorprone.XYDepricatedSingleMethodSingletonRule"
          externalInfoUrl="https://pmd.github.io/pmd-6.8.0/pmd_rules_java_errorprone.html#singlemethodsingleton">
        <description>
            Some classes contain overloaded getInstance. The problem with overloaded getInstance methods
            is that the instance created using the overloaded method is not cached and so,
            for each call and new objects will be created for every invocation.
        </description>
        <priority>2</priority>
        <example>
            <![CDATA[
    public class Singleton {

        private static Singleton singleton = new Singleton( );

        private Singleton(){ }

        public static Singleton getInstance( ) {
            return singleton;
        }

        public static Singleton getInstance(Object obj){
            Singleton singleton = (Singleton) obj;
            return singleton;           //violation
        }
    }
    ]]>
        </example>
    </rule>
</ruleset>
```

```java
public class XYDepricatedSingleMethodSingletonRule extends AbstractJavaRule {
    private Set<String> methodset = new HashSet<String>();

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        methodset.clear();
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        if (node.getResultType().isVoid()) {
            return super.visit(node, data);
        }

        if ("getInstance".equals(node.getMethodName())) {
            if (!methodset.add(node.getMethodName())) {
                addViolation(data, node);
            }
        }

        return super.visit(node, data);
    }
}
```