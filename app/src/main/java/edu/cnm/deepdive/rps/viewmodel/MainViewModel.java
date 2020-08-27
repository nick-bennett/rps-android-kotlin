/*
 *  Copyright 2020 Deep Dive Coding/CNM Ingenuity
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
package edu.cnm.deepdive.rps.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.OnLifecycleEvent;
import edu.cnm.deepdive.rps.model.Arena;

/**
 * Manages interaction with (and simulation execution of) an instance of {@link Arena}, and exposes
 * its key properties as LiveData.
 *
 * @author Nicholas Bennett
 */
public class MainViewModel extends AndroidViewModel implements LifecycleObserver {

  /**
   * Default number of breeds populating the {@link Arena} instance managed by this ViewModel.
   */
  public static final byte DEFAULT_NUM_BREEDS = 3;
  /**
   * Default size of the terrain used in the {@link Arena} instance managed by this ViewModel.
   */
  public static final int DEFAULT_ARENA_SIZE = 100;

  private static final int ITERATIONS_PER_SLEEP = DEFAULT_ARENA_SIZE * DEFAULT_ARENA_SIZE / 20;
  private static final int SLEEP_INTERVAL = 2;

  private final MutableLiveData<Arena> arena;
  private final MutableLiveData<Long> generation;
  private final MutableLiveData<Boolean> running;

  private Runner runner;

  /**
   * Initializes ViewModel and creates an instance of {@link Arena} with default constructor
   * paramater values.
   *
   * @param application
   */
  public MainViewModel(@NonNull Application application) {
    super(application);
    arena = new MutableLiveData<>(null);
    generation = new MutableLiveData<>(0L);
    running = new MutableLiveData<>(false);
    reset();
  }

  /**
   * Returns LiveData containing the current {@link Arena}.
   */
  public LiveData<Arena> getArena() {
    return arena;
  }

  /**
   * Returns LiveData containing a {@code boolean} flag indicating whether the simulation model is
   * currently running.
   */
  public LiveData<Boolean> isRunning() {
    return running;
  }

  /**
   * Returns LiveData containing the current {@code long} generation count of the {@link Arena}
   * instance.
   */
  public LiveData<Long> getGeneration() {
    return generation;
  }

  /**
   * Starts or resumes execution of the simulation of the {@link Arena} instance.
   */
  public void start() {
    stopRunner(false);
    running.setValue(true);
    startRunner();
  }

  /**
   * Pauses execution of the simulation of the {@link Arena} instance.
   */
  public void stop() {
    stopRunner(true);
    running.setValue(false);
  }

  /**
   * Resets the current {@link Arena} (if any). If an {@code Arena} has not yet been created,
   * creates one with the {@link #DEFAULT_NUM_BREEDS} breeds and {@link #DEFAULT_ARENA_SIZE} height
   * and width.
   */
  public void reset() {
    stop();
    Arena arena = this.arena.getValue();
    if (arena == null) {
      reset(DEFAULT_NUM_BREEDS, DEFAULT_ARENA_SIZE);
    } else {
      arena.init();
      generation.setValue(arena.getGeneration());
    }
  }

  /**
   * Creates a new {@link Arena} with the specified number of breeds and size.
   *
   * @param numBreeds number of breeds placed initially on the terrain of the new {@link Arena}.
   * @param arenaSize height and width of the terrain of the new {@link Arena}.
   */
  public void reset(byte numBreeds, int arenaSize) {
    stop();
    Arena arena = new Arena.Builder()
        .setNumBreeds(numBreeds)
        .setArenaSize(arenaSize)
        .build();
    arena.init();
    this.arena.setValue(arena);
    generation.setValue(arena.getGeneration());
  }

  @OnLifecycleEvent(Event.ON_PAUSE)
  private void pause() {
    //noinspection ConstantConditions
    stopRunner(!running.getValue());
  }

  @OnLifecycleEvent(Event.ON_RESUME)
  private void resume() {
    //noinspection ConstantConditions
    if (running.getValue()) {
      startRunner();
    }
  }

  private void startRunner() {
    runner = new Runner();
    runner.start();
  }

  private void stopRunner(boolean postOnStop) {
    if (runner != null) {
      runner.setPostOnStop(postOnStop);
      runner.setRunning(false);
      runner = null;
    }
  }

  private class Runner extends Thread {

    private boolean running = true;
    private boolean postOnStop;

    @Override
    public void run() {
      while (running) {
        Arena arena = MainViewModel.this.arena.getValue();
        try {
          if (arena != null) {
            for (int i = 0; i < ITERATIONS_PER_SLEEP; i++) {
              arena.advance();
            }
            running &= !arena.isAbsorbed();
          }
          generation.postValue(arena.getGeneration());
          sleep(SLEEP_INTERVAL);
        } catch (InterruptedException expected) {
          // Ignore innocuous exception.
        }
      }
      if (postOnStop) {
        MainViewModel.this.running.postValue(false);
      }
    }

    public void setRunning(boolean running) {
      this.running = running;
    }

    public void setPostOnStop(boolean postOnStop) {
      this.postOnStop = postOnStop;
    }

  }

}
