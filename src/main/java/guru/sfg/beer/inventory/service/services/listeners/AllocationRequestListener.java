package guru.sfg.beer.inventory.service.services.listeners;

import guru.sfg.beer.inventory.service.config.JmsConfig;
import guru.sfg.beer.inventory.service.services.AllocationService;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocateOrderResponse;
import guru.sfg.brewery.model.events.NewInventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationRequestListener {

    private final JmsTemplate jmsTemplate;
    private final AllocationService allocationService;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    public void listen(AllocateOrderRequest allocateOrderRequest) {
        AllocateOrderResponse.AllocateOrderResponseBuilder responseBuilder = AllocateOrderResponse.builder();
        responseBuilder.beerOrderDto(allocateOrderRequest.getBeerOrderDto());

        try {
            Boolean allocationResult = allocationService.allocateOrder(allocateOrderRequest.getBeerOrderDto());
            responseBuilder.pendingInventory(!allocationResult);
            responseBuilder.allocationError(false);
        } catch (Exception e) {
            log.error("Allocation faild for Order Id: " + allocateOrderRequest.getBeerOrderDto().getId());
            responseBuilder.allocationError(true);
        }

        jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE, responseBuilder.build());
    }
}
