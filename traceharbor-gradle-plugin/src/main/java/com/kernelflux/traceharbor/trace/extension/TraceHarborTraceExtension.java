package com.kernelflux.traceharbor.trace.extension;

import java.util.ArrayList;
import java.util.List;

public class TraceHarborTraceExtension {
    boolean transformInjectionForced;
    String baseMethodMapFile;
    String blackListFile;
    String customDexTransformName;
    boolean skipCheckClass = true; // skip by default

    boolean enable;

    /**
     * Inline equivalent of {@code -keeppackage} entries. Each value is a package prefix; classes
     * matching the prefix are skipped during instrumentation. Trailing {@code .**}, {@code .*}
     * or {@code .} are accepted for readability and stripped before matching.
     *
     * <p>Combined with {@link #blackListFile} — both sources contribute to the same block set.</p>
     */
    List<String> ignorePackages = new ArrayList<>();

    /**
     * Inline equivalent of {@code -keepclass} entries. Each value is a fully-qualified class name
     * (inner classes via {@code $}); only that exact class is skipped.
     */
    List<String> ignoreClasses = new ArrayList<>();

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void setBlackListFile(String blackListFile) {
        this.blackListFile = blackListFile;
    }

    public void setCustomDexTransformName(String customDexTransformName) {
        this.customDexTransformName = customDexTransformName;
    }

    public void setBaseMethodMapFile(String baseMethodMapFile) {
        this.baseMethodMapFile = baseMethodMapFile;
    }

    public void setTransformInjectionForced(boolean transformInjectionForced) {
        this.transformInjectionForced = transformInjectionForced;
    }

    public void setSkipCheckClass(boolean skipCheckClass) {
        this.skipCheckClass = skipCheckClass;
    }

    public String getBaseMethodMapFile() {
        return baseMethodMapFile;
    }

    public String getBlackListFile() {
        return blackListFile;
    }

    public String getCustomDexTransformName() {
        return customDexTransformName;
    }

    public boolean isTransformInjectionForced() {
        return transformInjectionForced;
    }

    public boolean isEnable() {
        return enable;
    }

    public boolean isSkipCheckClass() {
        return skipCheckClass;
    }

    public List<String> getIgnorePackages() {
        return ignorePackages;
    }

    public void setIgnorePackages(List<String> ignorePackages) {
        this.ignorePackages = (ignorePackages != null) ? ignorePackages : new ArrayList<String>();
    }

    public List<String> getIgnoreClasses() {
        return ignoreClasses;
    }

    public void setIgnoreClasses(List<String> ignoreClasses) {
        this.ignoreClasses = (ignoreClasses != null) ? ignoreClasses : new ArrayList<String>();
    }

    /** Convenience for {@code ignorePackage 'com.acme.foo'} style DSL calls. */
    public void ignorePackage(String pkg) {
        if (pkg != null && !pkg.isEmpty()) {
            this.ignorePackages.add(pkg);
        }
    }

    /** Convenience for {@code ignoreClass 'com.acme.foo.Bar'} style DSL calls. */
    public void ignoreClass(String cls) {
        if (cls != null && !cls.isEmpty()) {
            this.ignoreClasses.add(cls);
        }
    }
}
