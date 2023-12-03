package be.domotiqueinformatique.tools.factory;

import be.domotiqueinformatique.knx2mqtt.api.domain.GroupAddressInfo;
import be.domotiqueinformatique.knx2mqtt.common.domain.AbstractGroupAddressInfo;
import be.domotiqueinformatique.tools.factory.entity.LightEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.KNXFormatException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class EntitiesFactory {
    private final Logger L = Logger.getLogger(getClass().getName());

    private static final String LIGHTS = "lights";
    private static final String EXTRA = "extra";
    private final HashMap<String, JSONObject> configMap = new HashMap<>();
    private final List<String> globalErrorList = new ArrayList<>();

    public String getNewConfig(HashMap<String, AbstractGroupAddressInfo> gaTable) {
        HashMap<Integer, HashMap<Integer, List<GroupAddressInfo>>> bigBadMap = new HashMap<>();
        groupByGA(gaTable, bigBadMap);
        try {
            L.finest(new ObjectMapper().writeValueAsString(bigBadMap.get(1)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        buildEntities(bigBadMap);
        if (System.getenv().containsKey("KNX2MQTT_PATH_TO_EXTRA")) {
            try (Stream<Path> pathToExtra = Files.list(Path.of(System.getenv("KNX2MQTT_PATH_TO_EXTRA")))) {
                JSONObject extraJson = new JSONObject();
                pathToExtra
                        .map(EntitiesFactory::readFile)
                        .map(String::new)
                        .map(JSONObject::new)
                        .forEach(sourceJsonObject -> mergeJson(sourceJsonObject, extraJson));
                configMap.put(EXTRA, extraJson);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        JSONObject all = new JSONObject();
        configMap.keySet().stream().map(configMap::get).forEach(jsonObject -> mergeJson(jsonObject, all));
        L.fine(all.toString());
        if (System.getenv().containsKey("KNX2MQTT_PATH_TO_OUTPUT_JSON_FILE")) {
            try {
                Files.writeString(Path.of(System.getenv("KNX2MQTT_PATH_TO_OUTPUT_JSON_FILE")), all.toString(4));
                Files.writeString(Path.of("./run/errors.txt"), String.join("\n", globalErrorList));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return all.toString();
    }

    private static void mergeJson(JSONObject SourceJsonObject, JSONObject targetJsonObject) {
        SourceJsonObject.keySet().forEach(k -> targetJsonObject.put(k, SourceJsonObject.get(k)));
    }

    private static byte[] readFile(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void buildEntities(HashMap<Integer, HashMap<Integer, List<GroupAddressInfo>>> bigBadMap) {
        for (Map.Entry<Integer, HashMap<Integer, List<GroupAddressInfo>>> mainEntry : bigBadMap.entrySet()) {
            if (mainEntry.getKey() == 1) {
                for (Map.Entry<Integer, List<GroupAddressInfo>> subEntry : mainEntry.getValue().entrySet()) {
                    L.fine(STR. "Reading subEntry \{ subEntry.getKey() } with size \{ subEntry.getValue().size() }" );
                    LightEntity lightEntity = new LightEntity();
                    for (GroupAddressInfo groupAddressInfo : subEntry.getValue()) {
                        lightEntity = lightEntity.withParsableFunction(groupAddressInfo);
                    }
                    if (lightEntity.hasBlockingError()) {
                        lightEntity.getErrorList().forEach(L::warning);
                        this.globalErrorList.addAll(lightEntity.getErrorList());
                    } else {
                        configMap.computeIfAbsent(LIGHTS, o -> new JSONObject()).put(lightEntity.getName(), lightEntity.getJsonObject());
                    }
                }
            }
        }
    }

    private void groupByGA(HashMap<String, AbstractGroupAddressInfo> gaTable, HashMap<Integer, HashMap<Integer, List<GroupAddressInfo>>> bigBadMap) {
        for (Map.Entry<String, AbstractGroupAddressInfo> entry : gaTable.entrySet()) {
            try {
                GroupAddress groupAddress = new GroupAddress(entry.getValue().getAddress());
                if (groupAddress.getMainGroup() == 0 || groupAddress.getMiddleGroup() == 0 || groupAddress.getSubGroup8() == 0) { // commons
                    continue;
                }
                bigBadMap.computeIfAbsent(groupAddress.getMainGroup(), i -> new HashMap<>())
                        .computeIfAbsent(groupAddress.getSubGroup8(), i -> new ArrayList<>())
                        .add(entry.getValue());
            } catch (KNXFormatException e) {
                L.severe(e.getMessage());
            }
        }
    }
}
