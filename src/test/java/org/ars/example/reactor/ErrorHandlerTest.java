package org.ars.example.reactor;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
public class ErrorHandlerTest {

    @Test
    void staticFallbackValue() {
        var flux = Flux.just(1,2,0)
                .map(i -> "100/" + i + "=" + 100/i)
                .onErrorReturn("Divided by zero");
        flux.subscribe(log::info, log::error, () -> log.info("complete"));
    }
    @Test
    void catchAndSwallow() {
        var flux = Flux.just(1,2,0)
                .map(i -> "100/" + i + "=" + 100/i)
                .onErrorComplete(e -> true);
        flux.subscribe(log::info, log::error, () -> log.info("complete"));
    }
    @Test
    void fallbackMethod() {
        var flux = Flux.just("a","b","c")
                .flatMap(s -> s.equals("b") ?
                        Mono.just(s).map(i -> 0/0 + i).onErrorResume(e -> {
                            log.error("item:{}", s);
                            return Mono.just("e");
                        })
                        :
                        Flux.just(s+"1", s+"2"))
                .onErrorResume(e -> Mono.just("d"));
        flux.subscribe(log::info, log::error, () -> log.info("complete"));
    }
    @Test
    void onErrorResume() {
        Flux.range(1,3)
                .doOnNext(i -> System.out.println("input=" + i))
                .map(i -> i == 2 ? i / 0 : i)
                .onErrorResume(err -> {
                    log.info("onErrorResume");
                    return Flux.empty();
                })
                .reduce(Integer::sum)
                .doOnNext(i -> System.out.println("sum=" + i))
                .block();
    }
    @Test
    void onErrorContinue() {
        Flux.range(1,3)
                .doOnNext(i -> System.out.println("input=" + i))
                .map(i -> i == 2 ? i / 0 : i)
                .onErrorContinue((err, i) -> log.info("onErrorContinue={}", i))
                .reduce(Integer::sum)
                .doOnNext(i -> System.out.println("sum=" + i))
                .block();
    }

    /**
     * {@see https://devdojo.com/ketonemaniac/reactor-onerrorcontinue-vs-onerrorresume}
     */
    @Test
    void onErrorResumeOrContinue() {
        Flux.range(1,3)
                .doOnNext(i -> System.out.println("input=" + i))
                .map(i -> i == 2 ? i / 0 : i)
                .onErrorResume(err -> {
                    log.info("onErrorResume");
                    return Flux.empty();
                })
                .onErrorContinue((err, i) -> log.info("onErrorContinue={}", i))
                .reduce(Integer::sum)
                .doOnNext(i -> System.out.println("sum=" + i))
                .block();
    }
}
