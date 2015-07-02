# SimpleORM
A Bukkit plugin provides a central ORM Manager.

# Usage
Put it into your server's plugin folder, and start your server. 
It will generate default config file automatic.
```yaml
enables:
- default
dataSource:
  default:
    driver: com.mysql.jdbc.Driver
    url: jdbc:mysql://localhost/db
    userName: testUserName
    password: testPassword
```
Edit or add your own config entry follow this format.

# Developer
If you want to use this library in your own plugin. 
```java
public class MyPlugin extend JavaPlugin {

    @Override
    public void onLoad() {
        EbeanManager manager = EbeanManager.DEFAULT;
        // Replace 'default' with your config.
        EbeanHandler handler = manager.getHandler("default");
        // Add your entity class.
        handler.addClass(MyClass.class);
        ...
    }
    
    @Override
    public void onEnable() {
        EbeanManager manager = EbeanManager.DEFAULT;
        EbeanHandler handler = manager.getHandler("default");
        if (!handler.isInitialize) {
            try {
                handler.initialize(getClassLoader());
            } catch(Exception e) {
                getLogger().warning("DataSource not configured");
                setEnable(false);
            }
        }
        ...
    }
    
    public void function() {
        EbeanManager manager = EbeanManager.DEFAULT;
        EbeanHandler handler = manager.getHandler("default");
        MyClass my = handler.getServer().find(MyClass.class)
                                        .where()
                                        .eq("name", "zmy")
                                        .findUnique();
        System.out.print(my.getName());
        ...
}
        
```
