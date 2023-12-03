package be.domotiqueinformatique.tools.factory.entity;

import be.domotiqueinformatique.knx2mqtt.api.domain.GroupAddressInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractEntity<T extends AbstractEntity<T>> {
    protected static final String IGNORE = "IGNORE";
    protected static final String BLOCKING = "BLOCKING";
    protected static final String STATUS = "status";
    protected static final String SET = "set";
    protected boolean error = false;
    protected List<Error> errorList = new ArrayList<>();
    protected JSONObject mainJsonObject = new JSONObject();
    private String id;
    private String name;

    public String getId() {
        if (id == null) {
            return name.replace(" ", "_");
        }
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public T withName(String name) {
        this.name = name;
        return (T) this;
    }

    public List<String> getErrorList() {
        return errorList.stream().map(err -> STR. "\{ err.level }::\{ err.message }" ).collect(Collectors.toList());
    }

    public JSONObject getJsonObject() {
        return mainJsonObject;
    }

    public boolean hasBlockingError() {
        return errorList.stream().anyMatch(err -> err.level.equals(BLOCKING)) || hasNullValue(mainJsonObject);
    }

    private boolean hasNullValue(Object value) {
        if (value instanceof JSONObject jsonObject) {
            return jsonObject.keySet().stream().map(jsonObject::get).anyMatch(this::hasNullValue);
        } else if (value instanceof JSONArray) {
            return ((JSONArray) value).toList().stream().anyMatch(this::hasNullValue);
        } else {
            return value == null || "null".equals(value);
        }
    }

    public T withParsableFunction(GroupAddressInfo groupAddressInfo) {
        try {
            T t = parseFunction(groupAddressInfo);
            this.mainJsonObject = t.make();
            return t;
        } catch (Exception e) {
            errorList.add(new Error(BLOCKING, e.getMessage()));
            error = true;
            return (T) this;
        }
    }

    protected abstract T parseFunction(GroupAddressInfo groupAddressInfo);

    protected abstract JSONObject make();

    protected record Error(String level, String message) {
    }
}
