package be.domotiqueinformatique.tools.factory.entity;

import be.domotiqueinformatique.knx2mqtt.api.domain.GroupAddressInfo;
import org.json.JSONObject;

import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LightEntity extends AbstractEntity<LightEntity> {
    private final Set<LightFunction> functions = initFunctions();

    private Set<LightFunction> initFunctions() {
        return Set.of(
                new LightFunction(STATUS,
                        Pattern.compile(".*/Etat/.*statut$"),
                        Pattern.compile(".*/.*/(.*) statut$"),
                        this::setStateStatusTopic),
                new LightFunction(SET,
                        Pattern.compile(".*commutation$"),
                        Pattern.compile(".*/.*/(.*) commutation$"),
                        this::setStateSetTopic),
                new LightFunction(STATUS,
                        Pattern.compile(".*valeur$"),
                        Pattern.compile(".*/.*/(.*) valeur$"),
                        this::setBrightnessStatusTopic),
                new LightFunction(SET,
                        Pattern.compile(".*atténuation$"),
                        Pattern.compile(".*/.*/(.*) atténuation$"),
                        this::setBrightnessSetTopic),
                new LightFunction(IGNORE,
                        Pattern.compile(".*temporisation$"),
                        Pattern.compile(".*/.*/(.*) temporisation$"),
                        s -> {
                        }),
                new LightFunction(IGNORE,
                        Pattern.compile(".*remote control$"),
                        Pattern.compile(".*/.*/(.*) remote control$$"),
                        s -> {
                        }),
                new LightFunction(IGNORE,
                        Pattern.compile(".*valeur et statut$"),
                        Pattern.compile(".*/.*/(.*) valeur et statut$"),
                        this::setBrightnessStatusTopic)
        );
    }

    private String stateStatusTopic;
    private String stateSetTopic;
    private String brightnessStatusTopic;
    private String brightnessSetTopic;

    public String getStateStatusTopic() {
        return stateStatusTopic;
    }

    public void setStateStatusTopic(String stateStatusTopic) {
        this.stateStatusTopic = stateStatusTopic;
    }

    public String getStateSetTopic() {
        return stateSetTopic;
    }

    public void setStateSetTopic(String stateSetTopic) {
        this.stateSetTopic = stateSetTopic;
    }

    public String getBrightnessStatusTopic() {
        return brightnessStatusTopic;
    }

    public void setBrightnessStatusTopic(String brightnessStatusTopic) {
        this.brightnessStatusTopic = brightnessStatusTopic;
    }

    public String getBrightnessSetTopic() {
        return brightnessSetTopic;
    }

    public void setBrightnessSetTopic(String brightnessSetTopic) {
        this.brightnessSetTopic = brightnessSetTopic;
    }

    @Override
    protected LightEntity parseFunction(GroupAddressInfo groupAddressInfo) {
        LightFunction lightFunction = functions.stream()
                .filter(l -> l.functionPattern.matcher(groupAddressInfo.getName()).matches())
                .reduce((a, b) -> {
                    throw new IllegalStateException("Multiple elements: " + a + ", " + b);
                })
                .orElseThrow(() -> new IllegalStateException(STR. "No function with name \{ groupAddressInfo.getName() }" ));
        lightFunction.consumer.accept(STR. "knx2mqtt/text/\{ lightFunction.type }/\{ groupAddressInfo.getAddress() }" );
        if (IGNORE.equals(lightFunction.type)) {
            errorList.add(new Error(IGNORE, STR. "Ignored function : \{ groupAddressInfo.getName() } " ));
        }
        if (getName() == null) {
            Matcher matcher = lightFunction.namingPattern.matcher(groupAddressInfo.getName());
            if (matcher.matches()) {
                setName(matcher.group(1));
            }
        }
        return this;
    }

    protected JSONObject make() {
        return new JSONObject(STR
                . """
                {
                    "id": "\{ getId() }",
                    "name": "\{ getName() }",
                    "services": [
                        {
                            "name": "\{ getName() }",
                            "service": "Lightbulb",
                            "topic": {
                                "setOn": "\{ stateSetTopic }",
                                "statusOn": "\{ stateStatusTopic }"
                            },
                            "payload": {
                                "onTrue": "on",
                                "onFalse": "off"
                            }
                        }
                    ],
                    "manufacturer": "mqttBridge",
                    "model": "Light"
                }
                """ );
    }

    private String getBrightnessTopics() {
        if (brightnessSetTopic == null || brightnessStatusTopic == null || brightnessSetTopic.isEmpty() || brightnessStatusTopic.isBlank()) {
            return "";
        } else {
            return STR
                    . """
                    "getBrightness": "\{ brightnessStatusTopic }",
                    "setBrightness": "\{ brightnessSetTopic }",
                """ ;
        }
    }

    private record LightFunction(
            String type,
            Pattern functionPattern,
            Pattern namingPattern,
            Consumer<String> consumer
    ) {
    }
}
