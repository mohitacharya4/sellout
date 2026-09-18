package dev.sellout.platform.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Loads {@code META-INF/sellout-defaults.yaml} as the lowest-precedence property source. */
public final class SelloutDefaultsEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String RESOURCE = "META-INF/sellout-defaults.yaml";
  static final String SOURCE_NAME = "sellout-defaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    ClassPathResource resource = new ClassPathResource(RESOURCE);
    if (!resource.exists()) {
      throw new IllegalStateException("Missing platform defaults resource " + RESOURCE);
    }
    try {
      for (PropertySource<?> source : new YamlPropertySourceLoader().load(SOURCE_NAME, resource)) {
        environment.getPropertySources().addLast(source);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + RESOURCE, e);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
