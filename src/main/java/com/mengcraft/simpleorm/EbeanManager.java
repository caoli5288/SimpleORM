package com.mengcraft.simpleorm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EbeanManager {
    
    public static final EbeanManager DEFAULT = new EbeanManager();
    
    private final Map<String, EbeanHandler> map;
    
    private EbeanManager() {
        this.map = new HashMap<>();
    }
    
    public EbeanHandler getHandler(String name) {
        EbeanHandler out = map.get(name);
        return out != null ?
               out :
               create(name);
    }
    
    private EbeanHandler create(String name) {
        map.put(name, new EbeanHandler(name));
        return getHandler(name);
    }
    
    public Collection<EbeanHandler> handers() {
        return map.values();
    }

}
