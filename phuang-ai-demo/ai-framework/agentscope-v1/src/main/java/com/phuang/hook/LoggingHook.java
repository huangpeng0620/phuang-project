package com.phuang.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.PostCallEvent;
import reactor.core.publisher.Mono;

public class LoggingHook implements Hook {

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {

        if (event instanceof PreCallEvent) {
            System.out.println("智能体启动: " + event.getAgent().getName());
            return Mono.just(event);
        }

        if (event instanceof PostCallEvent) {
            System.out.println("智能体完成: " + event.getAgent().getName());
            return Mono.just(event);
        }

        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 10;  // 数字越小优先级越高（默认为 100）
    }

}
