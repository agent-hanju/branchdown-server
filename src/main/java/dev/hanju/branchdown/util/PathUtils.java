package dev.hanju.branchdown.util;

import java.util.Arrays;

/**
 * Path 파싱을 위한 유틸리티 클래스
 */
public class PathUtils {

  private PathUtils() {
  }

  /**
   * 쉼표로 구분된 path 문자열을 int 배열로 변환합니다.
   *
   * @param path 쉼표로 구분된 path 문자열 (예: "0,1,2" 또는 "")
   * @return int 배열
   * @throws IllegalStateException    path가 null인 경우
   * @throws IllegalArgumentException path 형식이 올바르지 않은 경우
   */
  public static int[] parse(final String path) {
    if (path == null) {
      throw new IllegalStateException("Path cannot be null");
    } else if (path.isEmpty()) {
      return new int[0];
    } else {
      try {
        return Arrays.stream(path.split(","))
            .mapToInt(Integer::parseInt)
            .toArray();
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException("Invalid path format: " + path, e);
      }
    }
  }

  /**
   * int 배열에 값이 없으면 추가한 새 배열을 반환합니다.
   *
   * @param path  원본 배열
   * @param value 추가할 값
   * @return 값이 없으면 추가된 새 배열, 이미 있으면 원본 배열
   */
  public static int[] append(final int[] path, final int value) {
    if (path == null) {
      throw new IllegalStateException("Path cannot be null");
    }
    if (contains(path, value)) {
      return path;
    }
    final int[] result = Arrays.copyOf(path, path.length + 1);
    result[path.length] = value;
    return result;
  }

  /**
   * int 배열에 값이 포함되어 있는지 반환합니다.
   *
   * @param path  검사할 배열
   * @param value 찾을 값
   * @return 값이 포함되어 있으면 true
   */
  public static boolean contains(final int[] path, final int value) {
    return indexOf(path, value) >= 0;
  }

  /**
   * int 배열에서 값의 첫 위치를 반환합니다.
   *
   * @param path  검사할 배열
   * @param value 찾을 값
   * @return 값의 첫 위치, 없으면 -1
   */
  public static int indexOf(final int[] path, final int value) {
    if (path == null) {
      throw new IllegalStateException("Path cannot be null");
    }
    for (int i = 0; i < path.length; i += 1) {
      if (path[i] == value) {
        return i;
      }
    }
    return -1;
  }

  /**
   * int 배열을 쉼표로 구분된 문자열로 변환합니다.
   *
   * @param values 변환할 배열
   * @return 쉼표로 구분된 문자열 (예: "0,1,2"), 빈 배열이면 ""
   */
  public static String joinWithComma(final int[] values) {
    if (values == null || values.length == 0) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(values[i]);
    }
    return sb.toString();
  }

  /**
   * IntArray 문자열에 값을 추가합니다.
   *
   * @param path  기존 IntArray 문자열 (예: "0,1" 또는 "")
   * @param value 추가할 값
   * @return 값이 추가된 새 IntArray 문자열
   */
  public static String append(final String path, final int value) {
    if (path == null) {
      throw new IllegalStateException("Path cannot be null");
    } else if (path.isEmpty()) {
      return String.valueOf(value);
    }
    return path + "," + value;
  }
}
