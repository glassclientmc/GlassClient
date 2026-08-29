package dev.glassclient.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/**
 * Mixin's bundled {@code IGlobalPropertyService} implementations (Blackboard
 * for LaunchWrapper/ModLauncher) reference those frameworks directly in
 * their constructors and throw NoClassDefFoundError standalone. This is a
 * plain in-memory replacement, registered via
 * META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService.
 */
public final class GlassClientPropertyService implements IGlobalPropertyService {

  private static final class Key implements IPropertyKey {
    private final String name;

    Key(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  private final Map<String, Object> properties = new ConcurrentHashMap<>();

  @Override
  public IPropertyKey resolveKey(String name) {
    return new Key(name);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getProperty(IPropertyKey key) {
    return (T) this.properties.get(key.toString());
  }

  @Override
  public void setProperty(IPropertyKey key, Object value) {
    this.properties.put(key.toString(), value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getProperty(IPropertyKey key, T defaultValue) {
    return (T) this.properties.getOrDefault(key.toString(), defaultValue);
  }

  @Override
  public String getPropertyString(IPropertyKey key, String defaultValue) {
    Object value = this.properties.get(key.toString());
    return value == null ? defaultValue : value.toString();
  }
}
