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
public class MyPlugin extends JavaPlugin {
    
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
        // ...
    }
    
    public void function() {
        MyClass my = getDatabase.find(MyClass.class)
                                .where()
                                .eq("name", "zmy")
                                .findUnique();
        System.out.print(my.getName());
        // ...
    }
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

## Redis wrapper

```groovy
import com.mengcraft.simpleorm.ORM
import com.mengcraft.simpleorm.RedisWrapper

RedisWrapper redisWrapper = ORM.globalRedisWrapper();
redisWrapper.open(redis -> {
    redis.set("my_key", "my_value");
    // more codes here
});

redisWrapper.subscribe("my_channel", message -> {
    Foo foo = Foo.decode(message);
    // codes here
});

redisWrapper.publish("my_channel", "my_message");
```

If you want to enable sentinel mode, make sure `master_name` is a non-empty string, or make sure it is `null`. An example is shown below.

```yaml
redis:
  master_name: i_am_master
  url: redis://host1:26379,host2:26379,host3:26379
  max_conn: 20
```

## Serializer

Simple serializer and deserializer based on Gson with `ConfigurationSerializable`, `ScriptObjectMirror` and `JSR310` supported.

```groovy
import com.google.common.base.Preconditions
import com.mengcraft.simpleorm.ORM
import org.bukkit.Bukkit
import org.bukkit.Location

Location loc = new Location(Bukkit.getWorld("world"), 0, 0, 0)

Map<String, Object> map = ORM.serialize(loc)
Location loc2 = ORM.deserialize(Location.class, map)

Preconditions.checkState(Objects.equals(loc, loc2))
```

## Async executors

Schedule async task to specific async pool or primary thread.

```jshelllanguage
import com.mengcraft.simpleorm.ORM;

import java.util.function.Supplier;

ORM.enqueue("pool_name", () -> "any_async_task")
        .thenComposeAsync(obj -> {
            // Codes here
        })

ORM.sync(() -> "any_sync_task")
        .thenAccept(s -> {
            // Codes here
        })
```

## Cluster

```groovy
#!/usr/bin/env groovy

import com.mengcraft.simpleorm.async.ClusterSystem

import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer

ClusterSystem.create("sample")// join(or create) named cluster and return its future. 
        .thenAccept(system -> {
            system.constructor(self -> {// construct system make it respawn automatic
                self.spawn("echo", actor -> {
                    actor.map(String.class, { sender, msg ->
                        println msg
                        return msg// just return
                    })
                })
            })
        })
```

## Flowable

```groovy
import com.mengcraft.simpleorm.Flowable

Flowable.of()
        .async()
        .complete({ "hello" })
        .orElse("")
        .sync()
        .then({ println it })
```