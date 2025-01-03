/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.accio.base.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.airlift.units.Duration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.accio.base.Utils.checkArgument;
import static java.util.Objects.requireNonNull;

public class Metric
        implements CacheInfo, Relationable
{
    private final String name;
    private final String baseObject;
    private final List<Column> dimension;
    private final List<Column> measure;
    private final List<TimeGrain> timeGrain;
    private final boolean cached;
    private final Duration refreshTime;
    private final String description;

    public static Metric metric(String name, String baseObject, List<Column> dimension, List<Column> measure)
    {
        return metric(name, baseObject, dimension, measure, List.of(), false);
    }

    public static Metric metric(String name, String baseObject, List<Column> dimension, List<Column> measure, List<TimeGrain> timeGrain)
    {
        return metric(name, baseObject, dimension, measure, timeGrain, false);
    }

    public static Metric metric(String name, String baseObject, List<Column> dimension, List<Column> measure, List<TimeGrain> timeGrain, boolean cached)
    {
        return metric(name, baseObject, dimension, measure, timeGrain, cached, null);
    }

    public static Metric metric(String name, String baseObject, List<Column> dimension, List<Column> measure, List<TimeGrain> timeGrain, boolean cached, String description)
    {
        return new Metric(name, baseObject, dimension, measure, timeGrain, cached, null, description);
    }

    @JsonCreator
    public Metric(
            @JsonProperty("name") String name,
            @JsonProperty("baseObject") String baseObject,
            @JsonProperty("dimension") List<Column> dimension,
            @JsonProperty("measure") List<Column> measure,
            @JsonProperty("timeGrain") List<TimeGrain> timeGrain,
            // preAggregated is deprecated, use cached instead.
            @JsonProperty("cached") @Deprecated @JsonAlias("preAggregated") boolean cached,
            @JsonProperty("refreshTime") Duration refreshTime,
            @JsonProperty("description") String description)
    {
        this.name = requireNonNull(name, "name is null");
        this.baseObject = requireNonNull(baseObject, "baseObject is null");
        this.dimension = requireNonNull(dimension, "dimension is null");
        this.measure = requireNonNull(measure, "measure is null");
        this.cached = cached;
        checkArgument(measure.size() > 0, "the number of measures should be one at least");
        this.timeGrain = requireNonNull(timeGrain, "timeGrain is null");
        this.refreshTime = refreshTime == null ? defaultRefreshTime : refreshTime;
        this.description = description;
    }

    @Override
    @JsonProperty
    public String getName()
    {
        return name;
    }

    @Override
    @JsonProperty
    public String getBaseObject()
    {
        return baseObject;
    }

    @JsonProperty
    public List<Column> getDimension()
    {
        return dimension;
    }

    @JsonProperty
    public List<Column> getMeasure()
    {
        return measure;
    }

    @JsonProperty
    public List<TimeGrain> getTimeGrain()
    {
        return timeGrain;
    }

    public Optional<TimeGrain> getTimeGrain(String timeGrainName)
    {
        return timeGrain.stream()
                .filter(timeGrain -> timeGrain.getName().equals(timeGrainName))
                .findFirst();
    }

    @Override
    public List<Column> getColumns()
    {
        return ImmutableList.<Column>builder().addAll(dimension).addAll(measure).build();
    }

    @Override
    @JsonProperty
    public boolean isCached()
    {
        return cached;
    }

    @Override
    @JsonProperty
    public Duration getRefreshTime()
    {
        return refreshTime;
    }

    @JsonProperty
    public String getDescription()
    {
        return description;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Metric that = (Metric) obj;
        return cached == that.cached
                && Objects.equals(name, that.name)
                && Objects.equals(baseObject, that.baseObject)
                && Objects.equals(dimension, that.dimension)
                && Objects.equals(measure, that.measure)
                && Objects.equals(timeGrain, that.timeGrain)
                && Objects.equals(refreshTime, that.refreshTime)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(
                name,
                baseObject,
                dimension,
                measure,
                timeGrain,
                cached,
                refreshTime,
                description);
    }

    @Override
    public String toString()
    {
        return "Metric{" +
                "name='" + name + '\'' +
                ", baseObject='" + baseObject + '\'' +
                ", dimension=" + dimension +
                ", measure=" + measure +
                ", timeGrain=" + timeGrain +
                ", cached=" + cached +
                ", refreshTime=" + refreshTime +
                ", description='" + description + '\'' +
                '}';
    }
}
