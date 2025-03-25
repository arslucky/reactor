package org.ars.example.reactor;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
                .handle((i, sink) -> {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        sink.error(new RuntimeException(e));
                        return;
                    }
                    sink.next(i);
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

    //Example of state-based generate
    @Test
    void generate() {
        var flux = Flux.generate(
                () -> 0,
                (state, sink) -> {
                    sink.next(state);
                    if (state >= 5) sink.complete();
                    return state + 1;
                },
                (state) -> log.info("producer: {}", state)
        );
        flux.subscribe(s -> log.info("subscriber: {}", s));
    }

    @Test
    void create() {
        var flux = Flux.create(sink -> {
            var events = new Integer[] {1,2,3,4,5};
            Stream.of(events).forEach(sink::next);
//            sink.complete();
        });
        flux.subscribe(log::info, log::error, () -> log.info("complete"));
    }

    @Test
    void push() {
        var flux = Flux.push(sink -> {
            var events = new Integer[] {1,2,3,4,5};
            Stream.of(events).forEach(sink::next);
//            sink.complete();
        });
        flux.subscribe(log::info, log::error, () -> log.info("complete"));
    }

    @Test
    void createInMultiThreading() {
        var sequenceCreator = new SequenceCreator();
        int numberElementsToEmit = 10_000;
        Flux<Integer> flux = Flux.create(sharedSink -> sequenceCreator.multiThreadSource(numberElementsToEmit, sharedSink));
        StepVerifier.create(flux)
                .expectNextCount(numberElementsToEmit)
                .verifyComplete();
    }
    /**
    Test has to be failed as push operator is only allowed for single thread processing
     @see <a href="https://projectreactor.io/docs/core/release/api/"/>
     @see <a href="https://stackoverflow.com/questions/58480997/what-is-the-difference-between-flux-create-vs-flux-push-in-project-reactor"/>
     */
    @Test
    void pushInMultiThreading() {
        var sequenceCreator = new SequenceCreator();
        int numberElementsToEmit = 10_000;
        Flux<Integer> flux = Flux.push(sharedSink -> sequenceCreator.multiThreadSource(numberElementsToEmit, sharedSink));
        StepVerifier.create(flux)
                .expectNextCount(numberElementsToEmit)
                .verifyComplete();
    }

    static class SequenceCreator {
        void multiThreadSource(Integer elementsToEmit, FluxSink<Integer> sharedSink) {
            var thread1 = new Thread(() -> emitElements(sharedSink, elementsToEmit/2), "Thread_1");
            var thread2 = new Thread(() -> emitElements(sharedSink, elementsToEmit/2), "Thread_2");

            thread1.start();
            thread2.start();

            try {
                thread1.join();
                thread2.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            sharedSink.complete();
        }
        void emitElements(FluxSink<Integer> sink, Integer count) {
            IntStream.range(1, count + 1).forEach(n -> {
                log.info("onNext {}", n);
                sink.next(n);
            });
        }
    }

    @Test
    void onRequest() {
        Flux<Integer> flux = Flux.create(sink -> {
            IntStream.range(0,3).forEach(emitItem -> {
                log.info("producer: emitted: {}", emitItem);
                sink.next(emitItem);
            });
            var arr = new Integer[] {10,20,30,40,50};

            var counter = new AtomicInteger();
            sink.onRequest(countOfRequestedItems -> { //TODO: Flux.push doesn't work as expected, countOfRequestedItems set to 9223372036854775807 Long.MAX_VALUE instead of remained/requested 1
                log.info("producer: requested of items: {}", countOfRequestedItems);
                for(var i = 1; i <= countOfRequestedItems; i++) {
                    if (counter.get() >= arr.length) {
                        log.info("producer: sent complete signal");
                        sink.complete();
                        break;
                    }
                    log.info("producer: emitted: {}", arr[counter.get()]);
                    sink.next(arr[counter.getAndIncrement()]);
                }
            });
        });
        class SimpleSubscriber extends BaseSubscriber<Integer> {
            final AtomicInteger requested = new AtomicInteger();
            final int batchSize = 3;
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                log.info("consumer: subscribed");
                requested.set(1);
                request(requested.get());
            }
            @Override
            protected void hookOnNext(Integer value) {
                log.info("consumer: got {}", value);
                if (requested.getAndDecrement() <= 1) {
                    requested.set(batchSize);
                    request(requested.get());
                }
            }
            @Override
            protected void hookOnComplete() {
                log.info("consumer: completed");
            }
            @Override
            protected void hookFinally(SignalType type) {
                log.info("consumer: finaled, signalType: {}", type);
            }
        }

        flux
            .doOnRequest(r -> log.info("consumer: request of {} items", r))
            .subscribe(new SimpleSubscriber());
    }

    @Test
    void publishOn() {
        var s = Schedulers.newParallel("parallel thread");
        var flux = Flux.range(1,2)
                .map(i -> {log.info("map1"); return i + 10;})
                .publishOn(s)
                .map(i -> {log.info("map2"); return "value" + i;});
        flux.subscribe(log::info);
    }

    @Test
    void subscribeOn() {
        var s = Schedulers.newParallel("parallel thread");
        var flux = Flux.range(1,2)
                .map(i -> {log.info("map1"); return i + 10;})
                .subscribeOn(s)
                .map(i -> {log.info("map2"); return "value" + i;});
        flux.subscribe(log::info);
    }
}
