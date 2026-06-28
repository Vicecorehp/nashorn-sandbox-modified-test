package lake.moon;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public class WhitelistConfig {

    private List<String> classes;
    private List<String> packages;

    public WhitelistConfig() {
    }

    public static WhitelistConfig load(String resourcePath) {
        var options = new LoaderOptions();
        var yaml = new Yaml(new Constructor(WhitelistConfig.class, options));
        try (var is = WhitelistConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Whitelist config not found: " + resourcePath);
            }
            return yaml.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isClassAllowed(String className) {
        if (classes != null && classes.contains(className)) {
            return true;
        }
        if (packages != null) {
            for (var pkg : packages) {
                if (className.equals(pkg) || className.startsWith(pkg + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }
}
