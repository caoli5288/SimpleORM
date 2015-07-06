# SimpleORM
A Bukkit plugin provides a central ORM Manager. It have no reload issue from
Bukkit's build-in ORM support.

# Usage
Put it into your server's plugin folder, and start your server. 

# Developer
If you want to use this library in your own plugin. code your main
class like this. See [Ebean ORM](http://avaje.org/ebean/documentation.html)
```java
public class MyPlugin extend JavaPlugin {

    private final EbeanManager manager = EbeanManager.DEFAULT;
    
    @Override
    public void onLoad() {
        EbeanHandler handler = manager.getHandler(this);
        // Define your entity class.
        handler.define(MyClass.class);
        handler.define(MyOther.class);
        ...
    }
    
    @Override
    public void onEnable() {
        EbeanHandler handler = manager.getHandler(this);
        if (!handler.isInitialize) {
            try {
                handler.initialize();
            } catch(Exception e) {
                // Do what you want to do.
            }
        }
        // This funtion will inject into Bukkit's build-in ORM support.
        handler.register();
        EbeanServer server = this.getDatabase();
        // EbeanServer server = handler.getServer();
        ...
    }
    
    public void function() {
        MyClass my = getDatabase.find(MyClass.class)
                                .where()
                                .eq("name", "zmy")
                                .findUnique();
        System.out.print(my.getName());
        ...
}
```
Code your entity class like this. See More [Example](https://github.com/ebean-orm/avaje-ebeanorm-examples/tree/master/a-basic/src/main/java/org/example/domain).
```java
@Entity
@Table(name = "o_table")
public class MyClass {
    
    @Id
    private int id;
    
    @Column(length=65535)
    private String name;
    
    // Put getter and setter.
    ...
}
```
Configure field will create automatic in your plguin's default config file.
Like this.
```yaml
dataSource:
  driver: com.mysql.jdbc.Driver
  url: jdbc:mysql://localhost/database
  userName: username
  password: password
```
