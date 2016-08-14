package com.mengcraft.simpleorm;

import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;

/**
 * Created on 16-8-15.
 */
public class PolicyInjector extends Policy {

    private final Policy origin;

    public PolicyInjector(Policy origin) {
        this.origin = origin;
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return permission instanceof javax.management.MBeanTrustPermission || origin.implies(domain, permission);
    }

    public static void inject() {
        Policy origin = Policy.getPolicy();
        if (!(origin instanceof PolicyInjector)) {
            Policy.setPolicy(new PolicyInjector(origin));
        }
    }

}
