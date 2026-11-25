package com.example.iknewit.common;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

public class UlidGenerator implements BeforeExecutionGenerator {

	@Override
	public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
		// monotonic: 같은 밀리초 안에서도 생성 순서대로 정렬되도록 보장 (chat_messages 순서 조회 등)
		return UlidCreator.getMonotonicUlid().toString();
	}

	@Override
	public EnumSet<EventType> getEventTypes() {
		return EnumSet.of(EventType.INSERT);
	}
}
