package io.fabric8.forge.introspection;

import org.jboss.forge.addon.convert.AbstractConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Converter for forge that creates a List from a comma-separated string
 */
public class StringToListConverter extends AbstractConverter<String, List> {

    public StringToListConverter(Class<?> sourceType, Class<?> targetType) {
        super((Class<String>)sourceType, (Class<List>)targetType);
    }

    @Override
    public List convert(String s) {
        List<String> answer = new ArrayList<String>();
        String[] parts = s.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                answer.add(part.trim());
            }
        }
        return answer;
    }
}
