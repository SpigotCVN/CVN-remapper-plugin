import org.bukkit.plugin.java.JavaPlugin;

public final class DummyJavaPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().disablePlugin(this);
    }
}
