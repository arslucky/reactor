package org.ars.example.reactor;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

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
    @Test
    void onErrorResumeOrContinue02() {
        Flux.range(1,3)
                .doOnNext(i -> System.out.println("input=" + i))
                .flatMap(i -> Mono.just(i)
                        .map(j -> j == 2 ? j / 0 : j)
                        .onErrorResume(err -> {
                            log.info("onErrorResume");
                            return Mono.empty();
                        })
                        .onErrorStop()
                )
                .onErrorContinue((err, i) -> log.info("onErrorContinue={}", i))
                .reduce(Integer::sum)
                .doOnNext(i -> System.out.println("sum=" + i))
                .block();
    }

    @Test
    void catchAndRethrow() {
        Flux.range(1,3)
                .doOnNext(System.out::println)
                .map(i -> i == 2 ? i/0 : i)
                .onErrorMap(err -> new BusinessException("something wrong", err))
                .doOnError(err -> log.error("error happened"))
                .blockLast();
    }
    static class BusinessException extends Exception {
        public BusinessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Test
    void finally01() {
        Flux.range(1,3)
                .doOnNext(System.out::println)
                .doOnSubscribe(sub -> System.out.println("subscribe"))
                .doFinally(signal -> System.out.println("signal:" + signal))
                .take(1L)
                .blockLast();
    }

    @SneakyThrows
    @Test
    void retry() {
        Flux.interval(Duration.ofMillis(100L))
                .map(input -> {
                    if (input < 3) return "tick " + input;
                    throw new RuntimeException("boom");
                })
                .retry(1)
                .subscribe(log::info, log::error);
        Thread.sleep(2000);
    }
    @SneakyThrows
    @Test
    void retryWhenInfiniteLoop() {
        Flux.interval(Duration.ofMillis(100L))
                .map(input -> {
                    if (input < 3) {
                        return "tick " + input;
                    } else {
                        log.info("throw an error");
                        throw new RuntimeException("boom");
                    }
                })
                .retryWhen(Retry.from(companion -> {
                    log.info("companion: {}", companion);
                    return companion.retry(1); //infinite loop
                }))
                .subscribe(log::info, log::error);
        Thread.sleep(3000);
    }

    @SneakyThrows
    @Test
    void retryWhenTake() {
        Flux.interval(Duration.ofMillis(100L))
                .map(input -> {
                    if (input < 3) {
                        return "tick " + input;
                    } else {
                        log.info("throw an error");
                        throw new RuntimeException("boom");
                    }
                })
                .retryWhen(Retry.from(companion -> {
                    log.info("companion: {}", companion);
                    return companion.take(2); // 2 includes the upstream first running, just 1 retry
                })) //completed sucessfull compare to retry(1), swallow an error
                .subscribe(log::info, log::error);
        Thread.sleep(3000);
    }

    @SneakyThrows
    @Test
    void retryWhenError() {
        Flux.interval(Duration.ofMillis(100L))
                .map(input -> {
                    if (input < 3) {
                        return "tick " + input;
                    } else {
                        log.info("throw an error");
                        throw new RuntimeException("boom");
                    }
                })
                .retryWhen(Retry.from(companion ->
                    companion.map(rs -> {
                        log.info("totalRetries: {}", rs.totalRetries());
                        if (rs.totalRetries() < 2) return rs.totalRetries();
                        throw Exceptions.propagate(rs.failure());
                    })
                ))
                .subscribe(log::info, log::error);
        Thread.sleep(3000);
    }
}
