package io.fabric8.forge.introspection;

import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.convert.ConverterGenerator;

import java.util.Objects;

/**
 * Generator for the StringToList converter
 */
public class StringToListConverterGenerator implements ConverterGenerator {
    @Override
    public boolean handles(Class<?> source, Class<?> target) {
        return Objects.equals(source.getName(), "java.lang.String") && Objects.equals(target.getName(), "java.util.List");
    }

    @Override
    public Converter<?, ?> generateConverter(Class<?> source, Class<?> target) {
        return new StringToListConverter(source, target);
    }

    @Override
    public Class<? extends Converter<?, ?>> getConverterType() {
        return StringToListConverter.class;
    }
}
