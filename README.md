# SimpleORM
A Bukkit plugin provides a central ORM Manager. It have no reload issue from
Bukkit's build-in ORM support.

## Usage
Put it into your server's plugin folder, and start your server,
or package source into your own plugin(not recommend).
This is a sample project [xKit](https://github.com/caoli5288/xKit).

## Developer
If you want to use this library in your own plugin. code your main
class like this. See [Ebean ORM](http://avaje.org/ebean/documentation.html)
```java
public class MyPlugin extend JavaPlugin {
    
    @Override
    public void onEnable() {
        /*
         * If you dont whan to use manager object, follow this
         * code. NOT RECOMMEND.
         *
         * EbeanHandler handler = new EbeanHandler();
         * handler.setDriver("com.mysql.jdbc.Driver");
         * handler.setUrl("jdbc:mysql://localhost:3306/database");
         * handler.setUserName("userName");
         * handler.setPassword("password");
         *
         * Use manager object will gen and read config field automatic.
         * and will store handler into a map, your can get it even
         * your plugin reload.
         *
         */
        // EbeanManager manager = EbeanManager.DEFAULT;
        EbeanManager manager = getServer().getServicesManager()
                .getRegistration(EbeanManager.class)
                .getProvider();
        EbeanHandler handler = manager.getHandler(this);

        if (!handler.isInitialize()) {
            handler.define(MyClass.class);
            handler.define(MyOther.class);
            try {
                handler.initialize();
            } catch(Exception e) {
                // Do what you want to do.
            }
        }
        // This function will inject into Bukkit's build-in 
        // ORM support.
        handler.reflect();
        // This function will try to create not exists tables.
        handler.install();
        // Injected build-in method. Return initialized 
        // Ebean server.
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
    
    @Column
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

## Attention
- A @ManyToOne field is lazy load!
- A @ManyToOne field is not support on sqlite platform!
