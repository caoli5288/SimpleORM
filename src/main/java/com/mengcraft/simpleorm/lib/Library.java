package com.mengcraft.simpleorm.lib;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Created on 15-12-13.
 */
public abstract class Library {

    public abstract File getFile();

    public void init() {
    }

    public boolean isLoadable() {
        return true;
    }

    public List<Library> getSublist() {
        return ImmutableList.of();
    }

}
