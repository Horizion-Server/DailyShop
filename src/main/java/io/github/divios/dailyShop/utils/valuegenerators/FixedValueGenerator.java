package io.github.divios.dailyShop.utils.valuegenerators;

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.divios.core_lib.misc.XSymbols;
import io.github.divios.dailyShop.utils.PrettyPrice;

public class FixedValueGenerator implements ValueGenerator {

    static JsonSerializerStrategy getJsonStrategy() {
        return element -> {
            JsonObject object = element.getAsJsonObject();

            Preconditions.checkArgument(object.has("fixed"), "No fixed value");

            double fixed = object.get("fixed").getAsDouble();
            return new FixedValueGenerator(fixed);
        };
    }

    public final double fixedValue;

    public FixedValueGenerator(double fixedValue) {
        this.fixedValue = fixedValue;
    }

    @Override
    public double generate() {
        return fixedValue;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("fixed", fixedValue);

        return json;
    }

    @Override
    public String toString() {
        return fixedValue <= -1
                ? "&c" + XSymbols.TIMES_3.parseSymbol()
                : PrettyPrice.pretty(fixedValue);
    }

    @Override
    public boolean isSimilar(ValueGenerator o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

        FixedValueGenerator that = (FixedValueGenerator) o;
        return Double.compare(fixedValue, that.fixedValue) == 0;
    }

}
