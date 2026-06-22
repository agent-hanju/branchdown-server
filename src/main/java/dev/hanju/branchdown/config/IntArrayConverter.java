package dev.hanju.branchdown.config;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** int 배열을 쉼표로 구분된 문자열로 변환하는 JPA Converter */
@Converter
public class IntArrayConverter implements AttributeConverter<int[], String> {

  private static final String DELIMITER = ",";

  @Override
  public String convertToDatabaseColumn(int[] attribute) {
    if (attribute == null) {
      return null;
    } else if (attribute.length == 0) {
      return "";
    } else {
      return IntStream.of(attribute)
          .mapToObj(String::valueOf)
          .collect(Collectors.joining(DELIMITER));
    }
  }

  @Override
  public int[] convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    } else if (dbData.isEmpty()) {
      return new int[0];
    }
    try {
      return Arrays.stream(dbData.split(DELIMITER))
          .mapToInt(Integer::parseInt)
          .toArray();
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid int array format in database: " + dbData, e);
    }
  }
}
