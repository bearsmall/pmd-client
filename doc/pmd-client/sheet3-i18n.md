# 可配置信息的国际化处理

## 读取配置文件

### Properties
> 1. 通常我们会使用Properties类作为读取配置文件的辅助类（如下代码所示）：
> 2. 该类继承Hashtable，将键值对存储在集合中。基于输入流从属性文件中读取键值对，load()方法调用完毕，就与输入流脱离关系，不会自动关闭输入流，需要手动关闭。
```java
public class PropertiesUtil {
    // 静态块中不能有非静态属性，所以加static
    private static Properties prop = null;

    //静态块中的内容会在类别加载的时候先被执行
    static {
        try {
            prop = new Properties();
            // prop.load(new FileInputStream(new File("C:\\jdbc.properties")));
            prop.load(PropertiesUtil.class.getClassLoader().getResourceAsStream("configs/jdbc.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //静态方法可以被类名直接调用
    public static String getValue(String key) {
        return prop.getProperty(key);
    }
}
```

### ResourceBundle
>1. 该类基于类读取属性文件：将属性文件当作类，意味着属性文件必须放在包中，使用属性文件的全限定性类名而非路径指代属性文件。
```java
    @Test
    public void test02() {
        ResourceBundle bundle = ResourceBundle.getBundle("com.javase.properties.test01");
        System.out.println("获取指定key的值");
        System.out.println("driver=" + bundle.getString("jdbc.driver"));
        System.out.println("url=" + bundle.getString("jdbc.url"));
        System.out.println("username=" + bundle.getString("jdbc.username"));
        System.out.println("password=" + bundle.getString("jdbc.password"));

        System.out.println("-----------------------------");

        Enumeration<String> keys = bundle.getKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            System.out.println(key + "=" + bundle.getString(key));
        }
    }
```

## 国际化

### ResourceBundle国际化
> 通过Locale参数指定区域语言，根据语言的不同会选用不同的配置文件，从而读取到适合特定Local的配置文件中的配置信息

```java
public class TestResourceBundle {

    public static void main(String[] args) { 
         Locale locale1 = new Locale("zh", "CN"); //通过local指定区域语言
         ResourceBundle resb1 = ResourceBundle.getBundle("myres", locale1); 
         System.out.println(resb1.getString("aaa")); 

         ResourceBundle resb2 = ResourceBundle.getBundle("myres", Locale.getDefault()); 
         System.out.println(resb1.getString("aaa")); 

         Locale locale3 = new Locale("en", "US"); 
         ResourceBundle resb3 = ResourceBundle.getBundle("myres", locale3); 
         System.out.println(resb3.getString("aaa")); 
    }
}
```
> 另外除了.properties文件，ResourceBundle同样可以支持xml格式的配置文件，其可读性更高
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <!--properties-->
    <entry key="java.singleton.SingletonShouldHaveOneGetInstanceMethod.violation.msg">
        <![CDATA[类com.xy.utils.Demo在单例模式下只应有一个getInstance()方法]]>
    </entry>
</properties>
```

### 配置文件包括运行时“变量”
>通过占位符的方式使得配置项动态可变，如下：
```java

    /**
     * 完全当字符串读取
     */
    public static String getMessage(String key) {
        return resourceBundle.getString(key).trim();
    }

    /**
     * 以格式化参数的方式读取，（运行时传参）
     */
    public static String getMessage(String key, Object... params) {
        String value = getMessage(key);
        if (params == null || params.length == 0) {
            return value;
        }
        return String.format(value, params);
    }
```

### 完整的工具类（com.xy.pmd.international.I18nResourceMapper）
> 1. 参考：https://docs.oracle.com/javase/8/docs/api/java/util/ResourceBundle.Control.html  example two
> 2. 客户端调用如下：

> 源文件
```java
public class I18nResourceMapperTest {
    public static final String MESSAGE_KEY_PREFIX = "java.singleton.SingletonShouldHaveOneGetInstanceMethod.violation.msg";

    @Test
    public void getMessage() {
        System.out.println(I18nResourceMapper.getMessage(MESSAGE_KEY_PREFIX));
    }

    @Test
    public void getMessage1() {
        System.out.println(I18nResourceMapper.getMessage(MESSAGE_KEY_PREFIX,I18nResourceMapper.class.getName()));
    }
}
```

> 配置文件 message_en.xml
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <!--properties-->
    <entry key="java.singleton.SingletonShouldHaveOneGetInstanceMethod.violation.msg">
        <![CDATA[the class 【%s】 should only have one getInstance() method when it is singleton]]>
    </entry>
</properties>
```

> 配置文件 message_zh.xml
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <!--properties-->
    <entry key="java.singleton.SingletonShouldHaveOneGetInstanceMethod.violation.msg">
        <![CDATA[类【%s】在单例模式下只应有一个getInstance()方法!!]]>
    </entry>
</properties>
```