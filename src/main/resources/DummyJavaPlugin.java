import org.bukkit.plugin.java.JavaPlugin;

public class DummyJavaPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().disablePlugin(this);
    }
}
