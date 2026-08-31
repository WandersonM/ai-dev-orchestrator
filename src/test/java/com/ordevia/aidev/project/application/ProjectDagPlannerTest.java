package com.ordevia.aidev.project.application;

import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemDependency;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemDependencyJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectDagPlannerTest {

    @Test
    void plansWavesAndOnlyExecutesItemsWithDoneBlockers() {
        UUID projectId = UUID.randomUUID();
        WorkItem a = item(projectId, "A");
        WorkItem b = item(projectId, "B");
        WorkItem c = item(projectId, "C");

        WorkItemJpaRepository items = mock(WorkItemJpaRepository.class);
        WorkItemDependencyJpaRepository dependencies = mock(WorkItemDependencyJpaRepository.class);
        when(items.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(a, b, c));
        when(dependencies.findByWorkItemIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(
                dependency(b, a),
                dependency(c, a)
        ));

        ProjectDagPlanner planner = new ProjectDagPlanner(items, dependencies);
        ProjectDagPlanner.DagPlan initial = planner.plan(projectId);

        assertThat(initial.valid()).isTrue();
        assertThat(initial.waves()).hasSize(2);
        assertThat(initial.waves().getFirst().items()).extracting(ProjectDagPlanner.WorkItemView::externalId).containsExactly("A");
        assertThat(initial.waves().get(1).items()).extracting(ProjectDagPlanner.WorkItemView::externalId).containsExactlyInAnyOrder("B", "C");
        assertThat(initial.executableNow()).extracting(ProjectDagPlanner.WorkItemView::externalId).containsExactly("A");

        a.moveTo(WorkItemStatus.DONE);
        ProjectDagPlanner.DagPlan afterMerge = planner.plan(projectId);
        assertThat(afterMerge.executableNow()).extracting(ProjectDagPlanner.WorkItemView::externalId).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    void detectsCycles() {
        UUID projectId = UUID.randomUUID();
        WorkItem a = item(projectId, "A");
        WorkItem b = item(projectId, "B");

        WorkItemJpaRepository items = mock(WorkItemJpaRepository.class);
        WorkItemDependencyJpaRepository dependencies = mock(WorkItemDependencyJpaRepository.class);
        when(items.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(a, b));
        when(dependencies.findByWorkItemIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(
                dependency(a, b),
                dependency(b, a)
        ));

        ProjectDagPlanner.DagPlan plan = new ProjectDagPlanner(items, dependencies).plan(projectId);

        assertThat(plan.valid()).isFalse();
        assertThat(plan.cyclicItems()).extracting(ProjectDagPlanner.WorkItemView::externalId).containsExactlyInAnyOrder("A", "B");
        assertThat(plan.executableNow()).isEmpty();
    }

    private WorkItem item(UUID projectId, String externalId) {
        return new WorkItem(UUID.randomUUID(), projectId, externalId, "Ticket " + externalId, "", "repositories/sample");
    }

    private WorkItemDependency dependency(WorkItem item, WorkItem blocker) {
        return new WorkItemDependency(UUID.randomUUID(), item.getId(), blocker.getId());
    }
}
