/************************************************************************
Copyright 2018 eBay Inc.
Author/Developer: Brendan McCarthy
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    https://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**************************************************************************/
package com.ebay.bascomtask.main;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for inline graph mods.
 * 
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS") // Simplistic
                                                                 // rule misses
                                                                 // valid usage
public class NestingTest extends PathTaskTestBase {

    @Test
    public void testExecuteTwiceStraightLine() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);

        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        B b = new B();
        PathTask taskB = track.work(b);
        verify(0);
        assertTrue(taskB.followed(taskA));
    }

    @Test
    public void testMultipleExecute() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        B b = new B();
        A a = new A();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        verify(1);

        class C extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        class D extends PathTask {
            @Work
            public void exec(C c) {
                got(c);
            }
        }
        C c = new C();
        D d = new D();

        PathTask taskC = track.work(c).exp(a);
        PathTask taskD = track.work(d).exp(c);
        verify(1);

        assertTrue(taskC.followed(taskA));
        assertTrue(taskD.followed(taskC));
    }

    @Test
    public void testSimpleNested() {
        class A extends PathTask {
            @Work
            public void exec() {
                class B extends PathTask {
                    @Work
                    public void exec() {
                        got();
                    }
                }
                B b = new B();
                PathTask taskB = track.work(b);
            }
        }
        A a = new A();
        PathTask taskA = track.work(a);
        verify(0);
    }

    @Test
    public void testNestedWithOuterDependency() {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        final A a = new A();
        class B extends PathTask {
            @Work
            public void exec() {
                class C extends PathTask {
                    @Work
                    public void exec(A a) {
                        got(a);
                    }
                }
                C c = new C();
                PathTask taskC = track.work(c).exp(a);
            }
        }
        B b = new B();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        verify(1);
    }

    private void addInnerDependency(boolean provides, final boolean addInner) {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        final A a = new A();
        class C extends PathTask {
            @Work
            public void exec() {
                if (addInner) {
                    // Add the 'a' that 'b' is looking for
                    PathTask taskA = track.work(a);
                }
            }
        }
        B b = new B();
        C c = new C();
        PathTask taskB = track.work(b).exp(a);
        PathTask taskC = track.work(c);
        if (provides) {
            taskC = taskC.provides(A.class);
        }
        verify(0);
    }

    @Test(expected = InvalidGraph.MissingDependents.class)
    public void testNestedAddWithoutProvides() {
        addInnerDependency(false,true);
    }

    @Test
    public void testNestedAddWithProvides() {
        addInnerDependency(true,true);
    }

    @Test(expected = InvalidGraph.ViolatedProvides.class)
    public void testProvidesNotSatisfied() {
        addInnerDependency(true,false);
    }

    private void testNestedBackwardDependency(boolean fork) {
        class A extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class B extends PathTask {
            @Work
            public void exec(A a) {
                got(a);
            }
        }
        A a1 = new A();
        final A a2 = new A(); // Not added immediately, on in nested exec
        B b = new B();
        class C extends PathTask {
            @Work
            public void exec() {
                sleep(20);
                PathTask taskA2 = track.work(a2);
            }
        }
        C c = new C();

        PathTask taskA = track.work(a1);
        PathTask taskB = track.work(b).exp(a1).exp(a2);
        PathTask taskC = track.work(c);
        if (fork) {
            // Forking, in combination with the sleep delay in C.exec, ensures
            // that a2 is only added after B completes; the orechestrator
            // execute
            // should still not complete until both a1-b and a2-b have
            // completed.
            taskC = taskC.fork();
        }
        verify(1);
    }

    @Test
    public void testNestedBackwardDependencyWithFork() {
        testNestedBackwardDependency(true);
    }

    @Test
    public void testNestedBackwardDependencyWithoutFork() {
        testNestedBackwardDependency(false);
    }

    @Test
    public void testNestedParllelization() {
        class A extends PathTask {
            private boolean it = false;

            @Work
            public void exec() {
                it = true;
                got();
            }

            boolean isIt() {
                return it;
            }
        }
        class B extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        class C extends PathTask {
            @Work
            public void exec() {
                got();
            }
        }
        final A a = new A();
        final B b = new B();
        final C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskB = track.work(b);
        PathTask taskC = track.work(c);

        class Record {
            PathTask taskShortWait;
            PathTask taskLongWait;
        }
        final int LONG_WAIT = 50;
        final int SHORT_WAIT = 10;
        final Record record = new Record();
        track.work(new PathTask() {
            @Work
            public void exec(A a) {
                class LongWaitDependsOnB extends PathTask {
                    @Work
                    public void exec(B b) {
                        sleep(LONG_WAIT);
                        got(b);
                    }
                }
                class ShortWaitDependsOnC extends PathTask {
                    @Work
                    public void exec(C c) {
                        sleep(SHORT_WAIT); // sleep a little but less than
                                           // LongWaitDependsOnB
                        got(c);
                    }
                }
                if (a.isIt()) {
                    LongWaitDependsOnB longWait = new LongWaitDependsOnB();
                    ShortWaitDependsOnC shortWait = new ShortWaitDependsOnC();
                    record.taskLongWait = track.work(longWait).exp(b);
                    record.taskShortWait = track.work(shortWait).exp(c);
                }
            }
        });

        verify(2,3);

        sleep(LONG_WAIT + 15);
        assertTrue(record.taskLongWait.followed(record.taskShortWait));
    }
    
    interface Nuthin {};
    
    private void invokNestingExplicit(final boolean beforeElseAfter) {
        class A extends PathTask implements Nuthin {
            @Work
            public void exec() {
                got();
            }
        }
        class B implements Nuthin {
            boolean bad = false;
            @Work
            public void exec(Nuthin n) {
                if (! (n instanceof A)) {
                    bad = true;
                }
            }
        }
        final B b = new B();
        class C extends PathTask {
            @Work
            public void exec(ITask task, A a) {
                got(a);
                Orchestrator orc = task.getOrchestrator();
                ITask taskB = orc.addWork(b);
                if (beforeElseAfter) {
                    orc.asAdded(a).before(b);
                }
                else {
                    taskB.after(a);
                }
            }
        }
        A a = new A();
        C c = new C();
        PathTask taskA = track.work(a);
        PathTask taskC = track.work(c).exp(a);
        verify(0);
        assertFalse(b.bad);
    }
    
    @Test
    public void testNestingExplicitBefore() {
        invokNestingExplicit(true);
    }

    @Test
    public void testNestingExplicitAfter() {
        invokNestingExplicit(false);
    }
    
    @Test(expected=InvalidGraph.Circular.class)
    public void testNestedCircular() {
        class A extends PathTask {
            @Work public void exec() {}
        }
        class B {
            @Work public void exec(B b) {} // Circular
        }
        class C extends PathTask {
            @Work
            public void exec(ITask task) {
                Orchestrator orc = task.getOrchestrator();
                B b = new B();
                ITask taskB = orc.addWork(b);
            }
        }
        A a = new A();
        C c = new C();
        PathTask taskA = track.work(a);
        // Fork so that circular dependency is thrown in sub-thread
        PathTask taskC = track.work(c).fork();
        verify(0);
    }
}
