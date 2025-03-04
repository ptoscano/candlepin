/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.util;

import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.model.CuratorException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Predicate;


/**
 * Genuinely random utilities.
 */
public class Util {

    private static final Logger log = LoggerFactory.getLogger(Util.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UTC_STR = "UTC";

    private Util() {
        // default ctor
    }

    /**
     * Generates a random UUID.
     *
     * @return a random UUID.
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a 32-character UUID to use with object creation/migration.
     * <p></p>
     * The UUID is generated by creating a "standard" UUID and removing the hyphens. The UUID may be
     * standardized by reinserting the hyphens later, if necessary.
     *
     * @return
     *  a 32-character UUID
     */
    public static String generateDbUUID() {
        return generateUUID().replace("-", "");
    }

    public static <T> Set<T> asSet(T... values) {
        Set<T> output = new HashSet<>(values.length);

        for (T value : values) {
            output.add(value);
        }

        return output;
    }

    public static Date tomorrow() {
        return addDaysToDt(1);
    }

    public static Date yesterday() {
        return addDaysToDt(-1);
    }

    public static Date midnight() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTime();
    }

    public static Date addDaysToDt(int dayField) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, dayField);
        return calendar.getTime();
    }

    public static Date addMinutesToDt(int minuteField) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, minuteField);
        return calendar.getTime();
    }

    /**
     * Converts the given {@link OffsetDateTime} to {@link Date}.
     *
     * @param dateTime date to convert
     * @return converted date
     */
    public static Date toDate(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.toInstant());
    }

    public static Date roundDownToSeconds(Date date) {
        return Date.from(date.toInstant().truncatedTo(ChronoUnit.SECONDS));
    }

    public static boolean equals(String str, String str1) {
        if (str == str1) {
            return true;
        }

        if ((str == null) ^ (str1 == null)) {
            return false;
        }

        return str.equals(str1);
    }

    public static long generateUniqueLong() {
        /*
          This deserves explanation.

          A random positive Long has 63 bits of hash space.  We want
          to have a given amount of certainty about the probability of
          collisions within this space.  This is an instance of the
          Birthday Problem[1].  We can get the probability that any
          two random numbers collide with the approximation:

          1-e**((-(N**2))/(2H))

          Where e is Euler's number, N is the number of random numbers
          generated, and H is the number of possible random outcomes.

          Suppose then that we generated one billion serials, with
          each serial being a 63-bit positive Long.  Then our
          probability of having a collision would be:

          irb(main):001:0> 1-Math.exp((-(1000000000.0**2))/(2.0*(2**63)))
          => 0.052766936243662

          So, if we generated a *billion* such serials, there is only
          a 5% chance that any two of them would be the same.  In
          other words, there is 95% chance that we would not have a
          single collision in one billion entries.

          The chances obviously get even less likely with smaller
          numbers.  With one million, the probability of a collision
          is:

          irb(main):002:0> 1-Math.exp((-(1000000.0**2))/(2.0*(2**63)))
          => 5.42101071809853e-08

          Or, 1 in 18,446,744.

          [1] http://en.wikipedia.org/wiki/Birthday_problem
         */

        long rnd;

        // Impl note:
        // Math.abs cannot negate MIN_VALUE, so we'll generate a new value when that happens.
        do {
            rnd = new SecureRandom().nextLong();
        }
        while (rnd == Long.MIN_VALUE);

        return Math.abs(rnd);
    }

    public static String toBase64(byte[] data) {
        try {
            // to be thread-safe, we should create it from the static method
            // If we don't specify the line separator, it will use CRLF
            return new String(new Base64(64, "\n".getBytes()).encode(data), "ASCII");
        }
        catch (UnsupportedEncodingException e) {
            log.warn("Unable to convert binary data to string", e);
            return new String(data);
        }
    }

    public static SimpleDateFormat getUTCDateFormat() {
        SimpleDateFormat iso8601DateFormat = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso8601DateFormat.setTimeZone(TimeZone.getTimeZone(UTC_STR));
        return iso8601DateFormat;
    }

    public static String readFile(InputStream is) {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
        }
        catch (IOException e) {
            throw new CuratorException(e);
        }
        finally {
            try {
                reader.close();
            }
            catch (IOException e) {
                log.warn("problem closing BufferedReader", e);
            }
        }
        return builder.toString();
    }

    public static String hash(String password) {
        //This is secure because even if the salt is known, a cracker
        //would still need to generate their own rainbow table, which
        //is the same as brute-forcing the password in the first place.

        String salt = "b669e3274a43f20769d3dedf03e9ac180e160f92";
        String combined = salt + password;

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        }

        try {
            md.update(combined.getBytes("UTF-8"), 0, combined.length());
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }

        byte[] sha1hash = md.digest();
        return new String(Hex.encodeHex(sha1hash));
    }


    public static String toJson(Object anObject) throws JsonProcessingException {
        return MAPPER.writeValueAsString(anObject);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        T output = null;
        try {
            output = MAPPER.readValue(json, clazz);
        }
        catch (Exception e) {
            log.error("Could no de-serialize the following json " + json, e);
        }
        return output;
    }

    @SuppressWarnings("rawtypes")
    public static String getClassName(Class c) {
        return getClassName(c.getName());
    }

    public static String getClassName(String fullClassName) {
        int firstChar = fullClassName.lastIndexOf('.') + 1;
        if (firstChar > 0) {
            fullClassName = fullClassName.substring(firstChar);
        }
        return fullClassName;
    }

    public static String reverseEndian(String in) {
        in = (in.length() % 2 != 0) ? "0" + in : in;
        StringBuilder sb = new StringBuilder();
        for (int i = in.length() - 2; i >= 0; i += (i % 2 == 0) ? 1 : -3) {
            sb.append(in.charAt(i));
        }
        return sb.toString();
    }

    public static String transformUuid(String uuid) {
        String[] partitions = uuid.split("-");
        List<String> newPartitions = new LinkedList<>();
        // We only want to revese the first three partitions
        for (int i = 0; i < partitions.length; i++) {
            newPartitions.add(i < 3 ? reverseEndian(partitions[i]) : partitions[i]);
        }
        return StringUtils.join(newPartitions, '-');
    }

    /*
     * Gets possible guest uuids regardless of endianness. When given a non-uuid,
     * this should return a list of length 1, with the given value.  All values
     * returned should be lower case
     */
    public static List<String> getPossibleUuids(String... ids) {
        List<String> results = new LinkedList<>();
        for (String id : ids) {
            if (id != null) {
                // We want to use lower case everywhere we can in order
                // to do less work at query time.
                id = id.toLowerCase();
            }
            results.add(id);
            if (isUuid(id)) {
                results.add(transformUuid(id));
            }
        }
        return results;
    }

    private static final String UUID_REGEX = "[a-fA-F0-9]{8}-" +
        "[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";

    public static boolean isUuid(String uuid) {
        return uuid != null && uuid.matches(UUID_REGEX);
    }

    public static String collectionToString(Collection c) {
        StringBuffer buf = new StringBuffer();
        for (Object o : c) {
            buf.append(o.toString());
            buf.append(" ");
        }
        return buf.toString();
    }

    /**
     * Compares two collections for equality without using the collection's equals method. This is
     * primarily only useful when working with collections that may actually be Hibernate bags, as
     * bags and proxies do not properly implement the equals method, which tends to lead to
     * incorrect results and unnecessary work.
     * <p></p>
     * WARNING: This method will not work with collections which use iterators that return its
     * elements in an inconsistent order. The order does not need to be known, but it must be
     * consistent and repeatable for a given collection state.
     *
     * @param c1
     *  A collection to compare to c2
     *
     * @param c2
     *  A collection to compare to c1
     *
     * @return
     *  true if both collections are the same instance, are both null or contain the same elements;
     *  false otherwise
     */
    public static <T> boolean collectionsAreEqual(Collection<T> c1, Collection<T> c2) {
        if (c1 == c2) {
            return true;
        }

        if (c1 == null || c2 == null || c1.size() != c2.size()) {
            return false;
        }

        Set<Integer> indexes = new HashSet<>();
        for (T lhs : c1) {
            boolean found = false;
            int offset = -1;

            for (T rhs : c2) {
                if (indexes.contains(++offset)) {
                    continue;
                }

                if (lhs == rhs || (lhs != null && lhs.equals(rhs))) {
                    indexes.add(offset);
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares two collections for equality without using the collection's equals method. This is
     * primarily only useful when working with collections that may actually be Hibernate bags, as
     * bags and proxies do not properly implement the equals method, which tends to lead to
     * incorrect results and unnecessary work.
     * <p></p>
     * WARNING: This method will not work with collections which use iterators that return its
     * elements in an inconsistent order. The order does not need to be known, but it must be
     * consistent and repeatable for a given collection state.
     *
     * @param c1
     *  A collection to compare to c2
     *
     * @param c2
     *  A collection to compare to c1
     *
     * @param comp
     *  A comparator to use to compare elements of c1 and c2 for equality
     *
     * @throws IllegalArgumentException
     *  if the provided compator is null
     *
     * @return
     *  true if both collections are the same instance, are both null or contain the same elements;
     *  false otherwise
     */
    public static <T> boolean collectionsAreEqual(Collection<T> c1, Collection<T> c2, Comparator<T> comp) {
        if (comp == null) {
            throw new IllegalArgumentException("comp is null");
        }

        if (c1 == c2) {
            return true;
        }

        if (c1 == null || c2 == null || c1.size() != c2.size()) {
            return false;
        }

        Set<Integer> indexes = new HashSet<>();
        for (T lhs : c1) {
            boolean found = false;
            int offset = -1;

            for (T rhs : c2) {
                if (indexes.contains(++offset)) {
                    continue;
                }

                if (lhs == rhs || (lhs != null && comp.compare(lhs, rhs) == 0)) {
                    indexes.add(offset);
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    /**
     * Fetches the hostname for this system without going through the network stack and DNS
     *
     * @return
     *  the hostname of this system
     */
    public static String getHostname() {
        try {
            // Hoping for the best here, as reflection rules in Java17 prevent us from
            // jumping directly to the getLocalHostName call without even more shenanigans
            // that are worse than just dealing with the shortcomings this method has.
            return InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the first non-null value in the provided values. If all of the provided values are
     * null, or no values are provided, this method returns null.
     *
     * @param values
     *  the values to examine
     *
     * @return
     *  the first non-null value of the values provided, or null if no such value is provided
     */
    public static <T> T firstOf(T... values) {
        if (values != null) {
            for (T value : values) {
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * Returns the first value from the given values which is validated by the provided predicate.
     * If none of the provided values are validated, or no values are provided, this method returns
     * null.
     *
     * @param predicate
     *  the predicate to use to find a value
     *
     * @param values
     *  the values to examine
     *
     * @return
     *  the first value which is validated by the provided predicate, or null if no such value is
     *  provided
     */
    public static <T> T firstOf(Predicate<T> predicate, T... values) {
        if (predicate == null) {
            throw new IllegalArgumentException("predicate is null");
        }

        if (values != null) {
            for (T value : values) {
                if (predicate.test(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * Splits a given string by comma and returns it as a List.
     *
     * Handles redundant whitespace and repeated commas.
     *
     * @param list a string to be split
     * @return a list of values
     */
    public static List<String> toList(String list) {
        if (StringUtils.isBlank(list)) {
            return Collections.emptyList();
        }
        return Arrays.asList(list.trim().split("\\s*,[\\s,]*"));
    }

    /*
     * Translates a given date into an OffsetDateTime with UTC timezone.
     *
     * @return
     *  OffsetDateTime or null if the given date is null
     */
    public static OffsetDateTime toDateTime(Date date) {
        return date != null ? date.toInstant().atOffset(ZoneOffset.UTC) : null;
    }

    /**
     * Converts a collection of Attribute DTOs to a standard map of strings. If the collection
     * contains multiple entries for the same attribute name, the last entry in the collection will
     * be used. Null attributes or attributes with null or empty keys will be silently discarded. If
     * the provided attribute collection is null, this function will return null.
     *
     * @param attributes
     *  a collection of attributes to convert
     *
     * @return
     *  a map containing the attribute names from the given collection mapped to their respective
     *  values, or null if the collection is null
     */
    public static Map<String, String> toMap(Collection<AttributeDTO> attributes) {
        if (attributes == null) {
            return null;
        }

        // Impl note:
        // At the time of writing, there is a bug/oddity in the collector returned by
        // Collectors.toMap which disallows null *values*. To retain the underlying
        // Hibernate behavior, we'll just silently discard attributes with null values.
        return attributes.stream()
            .filter(Objects::nonNull)
            .filter(attr -> attr.getName() != null && !attr.getName().isEmpty())
            .filter(attr -> attr.getValue() != null)
            .collect(HashMap::new, (map, attr) -> map.put(attr.getName(), attr.getValue()), HashMap::putAll);
    }

    public static OffsetDateTime parseOffsetDateTime(DateTimeFormatter formatter, String value) {
        TemporalAccessor temporalAccessor = formatter.parseBest(value,
            OffsetDateTime::from,
            ZonedDateTime::from,
            LocalDateTime::from,
            LocalDate::from);

        if (temporalAccessor instanceof OffsetDateTime || temporalAccessor instanceof ZonedDateTime) {
            return OffsetDateTime.from(temporalAccessor);
        }
        else if (temporalAccessor instanceof LocalDateTime) {
            return LocalDateTime.from(temporalAccessor).atOffset(ZoneOffset.UTC);
        }
        else {
            return LocalDate.from(temporalAccessor).atStartOfDay().atOffset(ZoneOffset.UTC);
        }
    }

    /**
     * Method encodes given text into a format usable in URLs in UTF-8 encoding.
     *
     * @param text a chunk of text to be encoded
     * @return encoded string
     */
    public static String encodeUrl(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /**
     * Returns the list items which got re-ordered in modified list.
     * Method only detect reordering of common elements present in both original
     * & modified list. Any extra elements will are not common (in both lists) are ignored.
     *
     * @return
     *  Returns the list of items which got re-prioritized
     */
    public static List<String> getReorderedItems(List<String> original, List<String> modified) {
        List<String> intersectionList = new ArrayList<>();
        List<String> reorderedList = new ArrayList<>();
        List<String> intersectionOfOriginal = new ArrayList<>();
        // build intersection list with ordering
        for (String envId : modified) {
            if (original.contains(envId)) {
                intersectionList.add(envId);
            }
        }

        for (String envId : original) {
            if (intersectionList.contains(envId)) {
                intersectionOfOriginal.add(envId);
            }
        }

        // check the ordering
        for (int count = 0; count < intersectionList.size(); count++) {
            if (!intersectionOfOriginal.get(count).equals(intersectionList.get(count))) {
                reorderedList.add(intersectionList.get(count));
            }
        }

        return reorderedList;
    }

}
