package jpabook.jpashop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Delivery {

    @Id @GeneratedValue
    @Column(name="delivery_id")
    private Long id;

    @OneToOne(mappedBy = "delivery")
    private Order order;

    @Embedded
    private Address address;

    // EnumType.ORDINAL이 기본 값이나 중간에 값이 생기면
    // 값이 밀리기 때문에 String으로 사용함
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status; // READY, COMP
}
