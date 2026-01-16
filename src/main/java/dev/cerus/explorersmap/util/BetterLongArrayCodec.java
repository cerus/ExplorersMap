package dev.cerus.explorersmap.util;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.codecs.array.LongArrayCodec;
import javax.annotation.Nonnull;
import org.bson.BsonArray;
import org.bson.BsonValue;

// A Long array codec that actually works
public class BetterLongArrayCodec extends LongArrayCodec {
    public static final BetterLongArrayCodec INSTANCE = new BetterLongArrayCodec();

    @Override
    public long[] decode(@Nonnull BsonValue bsonValue, ExtraInfo extraInfo) {
        BsonArray array = bsonValue.asArray();
        long[] longs = new long[array.size()];

        for(int i = 0; i < longs.length; ++i) {
            BsonValue element = array.get(i);
            if (element.isInt64()) {
                longs[i] = element.asInt64().getValue();
            } else if (element.isInt32()) {
                longs[i] = element.asInt32().getValue();
            } else {
                throw new IllegalArgumentException("Expected int32 or int64, but got " + element.getBsonType().name());
            }
        }

        return longs;
    }
}
