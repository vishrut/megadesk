/**
 *  Copyright 2014 LiveRamp
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.liveramp.megadesk.recipes.queue;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Test;

import com.liveramp.megadesk.base.state.InMemoryDriver;
import com.liveramp.megadesk.base.transaction.BaseDependency;
import com.liveramp.megadesk.base.transaction.BaseTransactionExecutor;
import com.liveramp.megadesk.core.state.Driver;
import com.liveramp.megadesk.core.transaction.Context;
import com.liveramp.megadesk.core.transaction.Dependency;
import com.liveramp.megadesk.recipes.gear.BaseGearIteration;
import com.liveramp.megadesk.recipes.gear.ConditionalGear;
import com.liveramp.megadesk.recipes.gear.Gear;
import com.liveramp.megadesk.recipes.gear.Outcome;
import com.liveramp.megadesk.recipes.iteration.BaseIterationExecutor;
import com.liveramp.megadesk.recipes.iteration.IterationExecutor;
import com.liveramp.megadesk.recipes.state.DriverFactory;
import com.liveramp.megadesk.test.BaseTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestQueue extends BaseTestCase {

  private final IterationExecutor iterationExecutor = new BaseIterationExecutor();

  @Test
  public void testBatching() {

    DriverFactory<ImmutableList<Integer>> listFactory = new BasicFactory<ImmutableList<Integer>>();
    DriverFactory<Boolean> boolFactory = new BasicFactory<Boolean>();
    BaseTransactionExecutor executor = new BaseTransactionExecutor();
    BatchExecutable<Integer> batch = BatchExecutable.getBatchByName("summed-integers", listFactory, boolFactory, executor);

    //basic batching
    batch.append(3);
    batch.append(2);
    List<Integer> list = batch.read();
    assertEquals(ImmutableList.of(3, 2), list);

    //call to readBatch should freeze the batch until it gets popped
    batch.append(10);
    list = batch.read();
    assertEquals(ImmutableList.of(3, 2), list);

    //pop, then read should give us the value appended earlier
    batch.pop();
    list = batch.read();
    assertEquals(ImmutableList.of(10), list);

    //Batches with the same name are the same
    BatchExecutable<Integer> sameBatchNewName = BatchExecutable.getBatchByName("summed-integers", listFactory, boolFactory, executor);
    List<Integer> newSum = sameBatchNewName.read();
    assertEquals(ImmutableList.of(10), newSum);

    sameBatchNewName.pop();

    //Reading an empty batch should still freeze the batch until pop is called
    assertTrue(sameBatchNewName.read().isEmpty());
    sameBatchNewName.append(10);
    assertTrue(sameBatchNewName.read().isEmpty());
  }

  @Test
  public void testQueue() {
    DriverFactory<ImmutableList<Integer>> listFactory = new BasicFactory<ImmutableList<Integer>>();
    DriverFactory<Boolean> boolFactory = new BasicFactory<Boolean>();
    QueueExecutable<Integer> queue = QueueExecutable.getQueueByName("integers", listFactory, boolFactory);

    queue.append(10);
    queue.append(11);
    queue.append(12);

    assertEquals(Integer.valueOf(10), queue.read());
    assertEquals(Integer.valueOf(10), queue.read());
    queue.pop();
    assertEquals(Integer.valueOf(11), queue.read());
    queue.pop();
    assertEquals(Integer.valueOf(12), queue.read());
  }

  @Test
  public void testQueueTransactions() throws InterruptedException {

    DriverFactory<ImmutableList<Integer>> listFactory = new BasicFactory<ImmutableList<Integer>>();
    DriverFactory<Boolean> boolFactory = new BasicFactory<Boolean>();
    QueueExecutable<Integer> input = QueueExecutable.getQueueByName("input", listFactory, boolFactory);
    QueueExecutable<Integer> output = QueueExecutable.getQueueByName("output", listFactory, boolFactory);

    MultiplyBy10 multiplyBy10 = new MultiplyBy10(input.getQueue(), output.getQueue());

    iterationExecutor.execute(new BaseGearIteration(multiplyBy10));

    input.append(10);
    input.append(20);
    input.append(30);
    input.append(-1);

    Thread.sleep(1000);

    assertEquals(Integer.valueOf(100), output.read());
    output.pop();
    assertEquals(Integer.valueOf(200), output.read());
    output.pop();
    assertEquals(Integer.valueOf(300), output.read());
    output.pop();
  }

  @Test
  public void testUnsafeBatch() {

    DriverFactory<ImmutableList<Integer>> listFactory = new BasicFactory<ImmutableList<Integer>>();
    DriverFactory<Boolean> boolFactory = new BasicFactory<Boolean>();
    BaseTransactionExecutor executor = new BaseTransactionExecutor();

    //Test with batches
    BatchExecutable<Integer> batch = BatchExecutable.getBatchByName("batch", listFactory, boolFactory, executor);
    UnsafeQueue<Integer> unsafeBatch = batch.getUnsafeQueue();

    batch.append(0);
    assertEquals(1, unsafeBatch.readInput().size());
    assertEquals(0, unsafeBatch.readOutput().size());

    batch.read();
    assertEquals(0, unsafeBatch.readInput().size());
    assertEquals(1, unsafeBatch.readOutput().size());

    unsafeBatch.writeInput(ImmutableList.of(0, 0));
    unsafeBatch.writeOutput(ImmutableList.of(0, 0, 0));
    assertEquals(2, unsafeBatch.readInput().size());
    assertEquals(3, unsafeBatch.readOutput().size());

    batch.pop();
    assertEquals(2, unsafeBatch.readInput().size());
    unsafeBatch.appendInput(Lists.newArrayList(0));
    assertEquals(3, unsafeBatch.readInput().size());
  }

  @Test
  public void testUnsafeQueue() {

    DriverFactory<ImmutableList<Integer>> listFactory = new BasicFactory<ImmutableList<Integer>>();
    DriverFactory<Boolean> boolFactory = new BasicFactory<Boolean>();
    BaseTransactionExecutor executor = new BaseTransactionExecutor();

    QueueExecutable<Integer> queue = QueueExecutable.getQueueByName("queue", listFactory, boolFactory);
    UnsafeQueue<Integer> unsafeQueue = queue.getUnsafeQueue();

    queue.append(1);
    unsafeQueue.appendInput(3);
    assertEquals(Integer.valueOf(1), queue.read());
    unsafeQueue.appendOutput(2);
    assertEquals(Integer.valueOf(1), queue.read());
    queue.pop();
    assertEquals(Integer.valueOf(3), queue.read());
    queue.pop();
    assertEquals(Integer.valueOf(2), queue.read());

  }

  private static class BasicFactory<T> implements DriverFactory<T> {

    private Map<String, Driver<T>> drivers = Maps.newHashMap();

    @Override
    public Driver<T> get(String referenceName, T initialValue) {
      if (!drivers.containsKey(referenceName)) {
        drivers.put(referenceName, new InMemoryDriver<T>(initialValue));
      }
      return drivers.get(referenceName);
    }
  }

  private static class MultiplyBy10 extends ConditionalGear implements Gear {

    private final Queue<Integer> inputQueue;
    private final Queue<Integer> outputQueue;

    private MultiplyBy10(Queue<Integer> inputQueue, Queue<Integer> outputQueue) {
      this.inputQueue = inputQueue;
      this.outputQueue = outputQueue;
      Dependency inputQueueDependecy = inputQueue.getPopDependency();
      Dependency outputQueueDependency = outputQueue.getAppendDependency();
      this.setDependency(BaseDependency.merge(inputQueueDependecy, outputQueueDependency));
    }

    @Override
    public Outcome check(Context context) {
      if (inputQueue.read(context) != null) {
        return Outcome.SUCCESS;
      } else {
        inputQueue.pop(context);
        return Outcome.STANDBY;
      }
    }

    @Override
    public Outcome execute(Context context) throws Exception {
      Integer integer = inputQueue.read(context);
      if (integer == -1) {
        return Outcome.ABANDON;
      }
      outputQueue.append(context, integer * 10);
      inputQueue.pop(context);
      return Outcome.SUCCESS;
    }
  }
}

