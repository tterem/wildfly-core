/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.moduleservice;

import static org.junit.Assert.assertArrayEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Verifies the oder of path comparator used in ExternalModuleSpecService is the correct.
 *
 * @author Yeray Borges
 */
public class PathComparatorTestCase {

    @Test
    public void pathComparatorOrderTest() {
        List<Path> pathsUnderTests = Arrays.asList(
                Paths.get("/C/E/A.txt"),
                Paths.get("/C/B/F/A.txt"),
                Paths.get("/A/A/A.txt"),
                Paths.get("/A/A/C.txt"),
                Paths.get("/A/B/B.txt"),
                Paths.get("/AB/C/A.txt"),
                Paths.get("/Z/A.txt"),
                Paths.get("/Z/B.txt"),
                Paths.get("/A/A.txt"),
                Paths.get("/A/B.txt"),
                Paths.get("/"),
                Paths.get("/B.txt"),
                Paths.get("/A.txt")
        );

        Collections.sort(pathsUnderTests, new ExternalModuleSpecService.PathComparator());

        List<Path> pathsExpectedOrder = Arrays.asList(
                Paths.get("/"),
                Paths.get("/A.txt"),
                Paths.get("/B.txt"),
                Paths.get("/A/A.txt"),
                Paths.get("/A/B.txt"),
                Paths.get("/A/A/A.txt"),
                Paths.get("/A/A/C.txt"),
                Paths.get("/A/B/B.txt"),
                Paths.get("/AB/C/A.txt"),
                Paths.get("/C/B/F/A.txt"),
                Paths.get("/C/E/A.txt"),
                Paths.get("/Z/A.txt"),
                Paths.get("/Z/B.txt")
        );

        assertArrayEquals("Unexpected order found", pathsUnderTests.toArray(), pathsExpectedOrder.toArray());
    }
}
