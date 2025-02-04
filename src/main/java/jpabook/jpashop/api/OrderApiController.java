package jpabook.jpashop.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.query.OrderFlatDto;
import jpabook.jpashop.repository.query.OrderQueryDto;
import jpabook.jpashop.repository.query.OrderQueryRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * 권장하는 방식 x
     */
    @GetMapping("/api/v1/orders")
    public List<Order> orderV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());

        for (Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();
            List<OrderItem> orderItems = order.getOrderItems();

            orderItems.stream().forEach(o -> o.getItem().getName());
        }

        return all;
    }

    /**
     * Dto 안에 엔티티가 있을 경우 Dto를 하나 더 생성하여 받아준다. 단 쿼리 실행이 많기 때문에 권장 x
     */
    @GetMapping("/api/v2/orders")
    public List<OrderDto> orderV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        List<OrderDto> collect = orders.stream().map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return collect;
    }

    /**
     * 패치 조인 활용
     * - 문제점 :
     * 1. 조인 해서 나온 결과 값만 큼 값이 늘어나서 나타남
     * - 해결법 :
     * 1. distinct 활용
     * - sql 문에서는 중복 제거가 안되지만
     * - JPA에서 자체적으로 Order 엔티티가 중복이면 알아서 제거해줌
     * - 단점
     * 1. 페이징 불가능, 사용이 가능해도 메모리에 올려서 하기 때문에 절대 하지 말 것
     * <p>
     * - 의문점 :
     * 1. 나는 잘나옴.. 버전이 올라가면서 결과값이 바뀐 듯? => 찾아본 결과 하이버네이트6 부터 패치조인 시 자동으로 distinct
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> orderV3() {
        List<Order> result = orderRepository.findAllWithItem();

        for (Order order : result) {
            System.out.println("order.getId() = " + order.getId());
        }
        List<OrderDto> collect = result.stream().map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return collect;
    }

    /**
     * 패치 조인 페이징과 한계 돌파
     * 1. 컬렌션을 패치 조인 하면 조인이 발생함으로 데이터가 예측할 수 없이 증가.
     * 2. Order를 기준으로 페이징을 하고 싶은데 OrderItem을 조인하면 OrderItem 기준이 되버림
     * 3. 이 경우 모든 데이터를 읽어서 메모리에서 피징 시도, 심하면 장애로 이어짐
     * <p>
     * 한계 돌파 :
     * 1. ToOne은 모두 페치 조인 한다.
     * 2. 컬렉션은 지연로딩으로 조회
     * 3. 지연 로딩 성능 최적화를 위해 hibernate.default_batch_fetch_size, @BatchSize 적용
     * - hibernate.default_batch_fetch_size : 글로벌 설정
     * - @BatchSize : 개별 최적화
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> orderV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> result = orderRepository.findAllWithMemberDelivery(offset, limit);

        List<OrderDto> collect = result.stream().map(o -> new OrderDto(o))
                .collect(Collectors.toList());

        return collect;
    }

    /**
     * Query: 루트 1번, 컬렉션 N 번 실행 ToOne(N:1, 1:1) 관계들을 먼저 조회하고,
     * <p>
     * ToMany(1:N) 관계는 각각 별도로 처리한다. 이런 방식을 선택한 이유는 다음과 같다.
     * <p>
     * 1. ToOne 관계는 조인해도 데이터 row 수가 증가하지 않는다.
     * 2. ToMany(1:N) 관계는 조인하면 row 수가 증가한다.
     * 3. row 수가 증가하지 않는 ToOne 관계는 조인으로 최적화 하기 쉬우므로 한번에 조회하고, ToMany 관계는 최적 화 하기 어려우므로 `findOrderItems()` 같은 별도의 메서드로
     * 조회한다.
     *
     * @return
     */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> orderV4() {
        return orderQueryRepository.findOrderQueryDtos();
    }


    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> orderV5() {
        return orderQueryRepository.findAllByDto_optimization();
    }

    /**
     * 권장 순서
     * 1. 엔티티 조회 방식으로 우선 접근
     * <p>
     * 1.1. 페치 조인으로 쿼리수 최적화
     * <p>
     * 1.2 컬렉션 최적화
     * 1.2.1. 페이징 필요 hibernate.default_batch_fetch_size` , `@BatchSize`로 최적화
     * 1.2.2  페이징 필요X -> 페치 조인 사용
     * <p>
     * 2. 엔티티 조회 방식으로 해결이 안돠면 DTO 조회 방식 사용
     * 3. DTO 조회 방식으로 해결이 안되면 NativeSQL or 스프링 JdbcTemplate
     */
    @GetMapping("/api/v6/orders")
    public List<OrderFlatDto> orderV6() {
        return orderQueryRepository.findAllByDto_flat();
    }

    @Getter
    static class OrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<orderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new orderItemDto(orderItem))
                    .collect(Collectors.toList());
        }
    }

    @Getter
    static class orderItemDto {
        private String itemName;
        private int orderPrice;
        private int count;

        public orderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}
