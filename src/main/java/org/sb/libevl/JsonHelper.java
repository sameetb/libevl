package org.sb.libevl;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonHelper
{
    public static String json(String name, Object val)
    {
        return quote(name) + " : " + quote(String.valueOf(val));
    }

    public static String obj(String... pairs)
    {
        return Arrays.stream(pairs).collect(Collectors.joining(",\n", "{\n", "}\n"));
    }
    
    private static String quote(String str)
    {
        return "\"" + str.replace("\"", "\\\"") + "\"";
    }
    
    public static String array(Object... objs)
    {
        return array(Arrays.stream(objs));
    }
    
    public static <T> String array(Stream<T> objs)
    {
        return objs.map(o -> String.valueOf(o)).collect(Collectors.joining(", ", "[ ", " ]"));
    }
}
