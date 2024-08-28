package iterative;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

public class ParallelMapperImpl implements ParallelMapper {

    /**
     * Threads for completing tasks.
     * Number of working threads defines by constructor argument.
     */
    private final List<Thread> threads = new ArrayList<>();

    private final Set<QueueProgress> inProcess = new HashSet<>();

    /**
     * Queue of tasks that waiting for completing.
     */
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    /**
     * Constructor that creates threads for working.
     *
     * @param threads - num of threads that would be created
     */
    public ParallelMapperImpl(final int threads) {
        final Runnable worker = () -> {
            try {
                while (!Thread.interrupted()) {
                    takeTask().run();
                }
            } catch (final InterruptedException ignored) {
            } finally {
                Thread.currentThread().interrupt();
            }
        };
        IntStream.range(0, threads).forEach(a -> {
            this.threads.add(new Thread(worker));
            this.threads.get(a).start();
        });
    }

    /**
     * Takes the first task in the queue, if possible.
     * If queue are empty - waits for task.
     *
     * @return task that was gotten from Queue.
     * @throws InterruptedException if thread were interrupted
     */
    private Runnable takeTask() throws InterruptedException {
        synchronized (tasks) {
            while (tasks.isEmpty()) {
                tasks.wait();
            }
            return tasks.poll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T, R> List<R> map(
            final Function<? super T, ? extends R> f,
            final List<? extends T> args
    ) throws InterruptedException {
        final List<R> result = new ArrayList<>(Collections.nCopies(args.size(), null));
        final QueueProgress control = new QueueProgress(args.size());
        synchronized (inProcess) {
            inProcess.add(control);
        }
        if (threads.isEmpty()) {
            synchronized (inProcess) {
                inProcess.remove(control);
            }
            throw new ProcessingException("No threads to compute result");
        }
        try {
            synchronized (tasks) {
                IntStream.range(0, args.size()).forEach(a -> {
                    tasks.add(() -> {
                        try {
                            final var res = f.apply(args.get(a));
                            result.set(a, res);
                        } catch (final RuntimeException e) {
                            control.addException(e);
                        } finally {
                            control.taskFinish();
                        }
                    });
                    tasks.notify();
                });
            }
            control.await();
        } finally {
            synchronized (inProcess) {
                inProcess.remove(control);
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        synchronized (inProcess) {
            for (final var thread : threads) {
                boolean closed = false;
                while (!closed) {
                    thread.interrupt();
                    try {
                        thread.join();
                        closed = true;
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            for (final QueueProgress control : inProcess) {
                control.forceFinish();
            }
        }
    }

    /**
     * A class that synchronized tracks the progress of a tasks queue
     */
    private static class QueueProgress {
        // :NOTE: ??
        /**
         * Number of tasks that completed by this moment.
         */
        private int c;

        private RuntimeException except = null;

        /**
         * @param tasksNum - number of tasks that should be completed.
         */
        QueueProgress(final int tasksNum) {
            c = tasksNum;
        }

        /**
         * Increases the counter for the number of completed tasks.
         * If all tasks are completed, it notifies.
         */
        public synchronized void taskFinish() {
            c--;
            if (isFinished()) {
                notify();
            }
        }

        public synchronized void addException(RuntimeException e) {
            if (except == null) {
                except = e;
            } else {
                except.addSuppressed(e);
            }
        }

        public synchronized void forceFinish() {
            c = 0;
            except = new ProcessingException("Process were closed during calculation");
            notify();
        }

        /**
         * Checks if all tasks are finished.
         *
         * @return true if all tasks are finished and else otherwise.
         */
        private boolean isFinished() {
            return c == 0;
        }

        private synchronized void await() throws InterruptedException {
            while (!isFinished()) {
                wait();
            }
            if (except != null) {
                throw except;
            }
        }
    }

    private static final class ProcessingException extends RuntimeException {
        private ProcessingException(String msg) {
            super(msg);
        }
    }
}
