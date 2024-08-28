package iterative;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IterativeParallelism implements NewListIP {
    /**Mapper to make task parallel without creating threads itself.
     */
    private final ParallelMapper mapper;

    /**Default constructor.
     * When using this constructor, during operations it will create threads as needed.
     */
    public IterativeParallelism() {
        this.mapper = null;
    }

    /** When using this constructor, no additional threads are created.
     * The creation of threads and calculation of functions is delegated to parallelMapper
     * @param mapper - mapper to parallel tasks.
     */

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    /** Record to grep view and function to provide to the thread.
     * @param view - view of a target list.
     * @param toRun - function to process
     * @param <T> - type that extends function argument
     * @param <R> - type of function result
     */
    private record ViewGrep<T, R>(List<? extends T> view, Function<List<? extends T>, R> toRun) implements Runnable {
        @Override
        public void run() {
            toRun.apply(view);
        }

    }

    /** Class that wrapping a list to provide access to only a part of the elements.
     * @param <T> - type of elements in provided list
     */
    private static class StepWrapper<T> extends AbstractList<T> implements RandomAccess {
        /**
         * Contains provided list
         */
        private final List<T> data;
        /**
         * Defining access step
         */
        private final int step;

        StepWrapper(int step, List<T> data) {
            this.data = data;
            this.step = step;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T get(int index) {
            return data.get(step * index);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size() {
            return (int)Math.ceil((double)data.size() / step);
        }
    }

    /** Makes parallel the task execution by splitting the sheet into sub-sheets,
     * then the result of partial processing is collected.
     * @param threadsNum - num of threads that method could create
     * @param step - every step list element would be processed
     * @param inpData - list to be processed
     * @param thTask - function to be executed with each thread on its partition of data
     * @param resSolver - function to be executed on list of threads results
     * @return result of execution on list.
     * @param <T> - type that extends input list elements
     * @param <P> - type of result of thTask function
     * @param <R> - type of function result. That type must be the super type of resSolver result type.
     * @throws InterruptedException if one of threads were interrupted
     */
    private <T, P, R> R makeParallel(
            int threadsNum,
            int step,
            List<T> inpData,
            Function<List<? extends T>, P> thTask,
            Function<List<P>, ? extends R> resSolver
    ) throws InterruptedException {
        StepWrapper<T> data = new StepWrapper<>(step, inpData);
        List<Thread> threads = new ArrayList<>(threadsNum);
        List<P> partialRes = new ArrayList<>(Collections.nCopies(Math.min(threadsNum, data.size()), null));
        int standartPart = data.size() / threadsNum;
        int extendedParts = data.size() % threadsNum;
        int from = 0;
        List<List<? extends T>> partials = new ArrayList<>();
        for (int j = 0; j < Math.min(threadsNum, data.size()); j++) {
            int to = from + standartPart + ((j < extendedParts) ? 1 : 0);
            if (mapper == null) {
                int finalJ = j;
                threads.add(j, new Thread(new ViewGrep<T, P>(
                        data.subList(from, to),
                        x -> partialRes.set(finalJ, thTask.apply(x))))
                );
                threads.get(j).start();
            } else {
                partials.add(data.subList(from, to));
            }
            from = to;
        }

        InterruptedException lastExcept = null;
        if (mapper == null) {
            for (Thread thread : threads) {
                boolean closed = false;
                while (!closed) {
                    try {
                        thread.join();
                        closed = true;
                    } catch (InterruptedException e) {
                        lastExcept = e;
                    }
                }
            }
        } else {
            return resSolver.apply(mapper.map(thTask, partials));
        }

        if (lastExcept != null) {
            throw lastExcept;
        }
        return resSolver.apply(partialRes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String join(int i, List<?> list, int step) throws InterruptedException {
        return makeParallel(
                i,
                step,
                list,
                x -> x.stream().map(Object::toString).collect(Collectors.joining()),
                x -> String.join("", x)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<T> filter(
            int i,
            List<? extends T> list,
            Predicate<? super T> predicate,
            int step
    ) throws InterruptedException {
        return makeParallel(
                i,
                step,
                list,
                x -> x.stream().filter(predicate).toList(),
                x -> x.stream().flatMap(List::stream).map(a -> (T) a).toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T, U> List<U> map(
            int i,
            List<? extends T> list,
            Function<? super T, ? extends U> function,
            int step
    ) throws InterruptedException {
        return makeParallel(
                i,
                step,
                list,
                x -> x.stream().map(function).toList(),
                x -> x.stream().flatMap(List::stream).map(a -> (U) a).toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T maximum(
            int i,
            List<? extends T> list,
            Comparator<? super T> comparator,
            int step) throws InterruptedException {
        return makeParallel(
                i,
                step,
                list,
                x -> x.stream().max(comparator).orElseThrow(),
                x -> x.stream().max(comparator).orElseThrow()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T minimum(
            int i,
            List<? extends T> list,
            Comparator<? super T> comparator,
            int step
    ) throws InterruptedException {
        return maximum(i, list, comparator.reversed(), step);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean all(
            int i,
            List<? extends T> list,
            Predicate<? super T> predicate,
            int step
    ) throws InterruptedException {
        return makeParallel(i, step, list, x -> x.stream().allMatch(predicate), x -> x.stream().allMatch(a -> a));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> boolean any(
            int i,
            List<? extends T> list,
            Predicate<? super T> predicate,
            int step
    ) throws InterruptedException {
        return makeParallel(i, step, list, x -> x.stream().anyMatch(predicate), x -> x.stream().anyMatch(a -> a));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int count(
            int i,
            List<? extends T> list,
            Predicate<? super T> predicate,
            int step
    ) throws InterruptedException {
        return makeParallel(
                i,
                step,
                list,
                x -> x.stream().filter(predicate).count(),
                x -> x.stream().mapToInt(Math::toIntExact).sum()
        );
    }
}
