package com.ssmt.project;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Links ship CSV names and descriptions by exact ID within the same mod subtree. */
final class ShipTranslationContext {
    private static final Pattern CSV_KEY = Pattern.compile("^csv:([^=]+)=([^:]+):(.+)$");
    private final Map<Ship, String> contexts;

    ShipTranslationContext(LocalizationProject project) {
        Map<Ship, StringBuilder> grouped = new LinkedHashMap<>();
        for (ProjectEntry entry : project.entries()) {
            ship(entry).ifPresent(ship -> grouped.computeIfAbsent(ship,
                    ignored -> new StringBuilder("Ship ID: ").append(ship.id()))
                    .append('\n').append(entry.sourceFile().toString().replace('\\', '/'))
                    .append('#').append(entry.key()).append(": ").append(entry.originalText()));
        }
        contexts = new HashMap<>();
        grouped.forEach((ship, text) -> contexts.put(ship, text.toString()));
    }

    String forEntry(ProjectEntry entry) {
        return ship(entry).map(contexts::get).orElse("");
    }

    private static Optional<Ship> ship(ProjectEntry entry) {
        String path = entry.sourceFile().toString().replace('\\', '/');
        String ships = "data/hulls/ship_data.csv";
        String descriptions = "data/strings/descriptions.csv";
        boolean shipData = path.equals(ships) || path.endsWith("/" + ships);
        boolean description = path.equals(descriptions) || path.endsWith("/" + descriptions);
        var matcher = CSV_KEY.matcher(entry.key());
        if ((!shipData && !description) || !matcher.matches()) {
            return Optional.empty();
        }
        String columns = decode(matcher.group(1));
        String identity = decode(matcher.group(2));
        String id;
        if (shipData && columns.equals("id")) {
            id = identity;
        } else if (description && columns.equals("id,type")) {
            String[] values = identity.split("\0", -1);
            if (values.length != 2 || !values[1].equals("SHIP")) {
                return Optional.empty();
            }
            id = values[0];
        } else {
            return Optional.empty();
        }
        String suffix = shipData ? ships : descriptions;
        return Optional.of(new Ship(path.substring(0, path.length() - suffix.length()), id));
    }

    private static String decode(String value) {
        return value.replace("%00", "\0").replace("%2C", ",")
                .replace("%3D", "=").replace("%3A", ":").replace("%25", "%");
    }

    private record Ship(String subtree, String id) {
    }
}
