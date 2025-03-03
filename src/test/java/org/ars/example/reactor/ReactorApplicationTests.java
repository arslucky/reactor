package org.ars.example.reactor;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

@Log4j2
@SpringBootTest
class ReactorApplicationTests {

    @Test
    void simpleFlux() {
        Flux<String> fluxColors = Flux.just("red", "yellow", "green");
        fluxColors.subscribe(System.out::println);
    }

    @Test
    void publishSubscriber() {
        var schedulerA = Schedulers.newParallel("Scheduler A");
        var schedulerB = Schedulers.newParallel("Scheduler B");
        var schedulerC = Schedulers.newParallel("Scheduler C");

        Flux.just(1)
                .map(i -> {
                    System.out.println("First map: " + Thread.currentThread().getName());
                    return i;
                })
                .subscribeOn(schedulerA)
                .map(i -> {
                    System.out.println("Second map: " + Thread.currentThread().getName());
                    return i;
                })
                .publishOn(schedulerB)
                .map(i -> {
                    System.out.println("Third map: " + Thread.currentThread().getName());
                    return i;
                })
                .subscribeOn(schedulerC)
                .map(i -> {
                    System.out.println("Fourth map: " + Thread.currentThread().getName());
                    return i;
                })
                .publishOn(schedulerA)
                .map(i -> {
                    System.out.println("Fifth map: " + Thread.currentThread().getName());
                    return i;
                })
                .blockLast();
    }

    @SneakyThrows
    @Test
    void dispose() {
        var publisher = Flux.range(1, 5)
                .map(i -> {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return i;
                });
        var disposable = publisher
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(log::info, log::error, () -> log.info("complete"));
        Thread.sleep(1500L);
        disposable.dispose();
        Thread.sleep(2000L);
    }

    @Test
    void baseSubscriber () {
        class SimpleSubscriber extends BaseSubscriber<Integer> {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                log.info("subscribed");
                request(1L);
            }

            @Override
            protected void hookOnNext(Integer value) {
                log.info(value);
                if(value>2) {
                    log.info("canceling after having received: {}", value);
                    cancel();
                }
                request(1);
            }

            @Override
            protected void hookOnComplete() {
                log.info("completed");
            }

            @Override
            protected void hookFinally(SignalType type) {
                log.info("finaled, signalType: {}", type);
            }
        }

        var flux = Flux.range(1,4);
        flux
            .doOnRequest(r -> log.info("request of {}", r))
            .subscribe(new SimpleSubscriber());
    }
}
