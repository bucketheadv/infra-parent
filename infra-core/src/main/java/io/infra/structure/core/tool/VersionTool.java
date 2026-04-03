package io.infra.structure.core.tool;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author sven
 * Created on 2026/4/2 10:33
 */
@UtilityClass
public class VersionTool {
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^(\\d+)(?:-?([0-9A-Za-z]+))?$");

    public int compare(String leftVersion, String rightVersion) {
        if (StringUtils.isBlank(leftVersion) || StringUtils.isBlank(rightVersion)) {
            throw new IllegalArgumentException("version must not be blank");
        }

        List<VersionSegment> leftSegments = parse(leftVersion);
        List<VersionSegment> rightSegments = parse(rightVersion);
        int max = Math.max(leftSegments.size(), rightSegments.size());
        for (int i = 0; i < max; i++) {
            VersionSegment left = i < leftSegments.size() ? leftSegments.get(i) : VersionSegment.ZERO;
            VersionSegment right = i < rightSegments.size() ? rightSegments.get(i) : VersionSegment.ZERO;
            int result = Long.compare(left.number, right.number);
            if (result != 0) {
                return result;
            }
            result = compareSuffix(left.suffix, right.suffix);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    public boolean isGreaterThan(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) > 0;
    }

    public boolean isLessThan(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) < 0;
    }

    public boolean isEquals(String leftVersion, String rightVersion) {
        return compare(leftVersion, rightVersion) == 0;
    }

    private int compareSuffix(String left, String right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        // 无后缀视为正式版本，优先级高于带后缀版本
        if (StringUtils.isEmpty(left)) {
            return 1;
        }
        if (StringUtils.isEmpty(right)) {
            return -1;
        }
        int result = String.CASE_INSENSITIVE_ORDER.compare(left, right);
        if (result != 0) {
            return result;
        }
        return left.compareTo(right);
    }

    private List<VersionSegment> parse(String version) {
        String[] rawSegments = StringUtils.split(version, '.');
        List<VersionSegment> segments = new ArrayList<>(rawSegments.length);
        for (String rawSegment : rawSegments) {
            Matcher matcher = SEGMENT_PATTERN.matcher(rawSegment);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid version: " + version);
            }
            long number = Long.parseLong(matcher.group(1));
            String suffix = matcher.group(2);
            segments.add(new VersionSegment(number, suffix == null ? "" : suffix));
        }
        return segments;
    }

    private record VersionSegment(long number, String suffix) {
            private static final VersionSegment ZERO = new VersionSegment(0L, "");
    }
}
