package jnu.econovation.isekai.rtzr.cache.model.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash(value = "AccessToken", timeToLive = 3600 * 6 - 60)
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RtzrAccessToken {

    @Id
    private String id;
    private String value;
}