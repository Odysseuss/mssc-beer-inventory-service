package guru.sfg.beer.inventory.service.services;

import guru.sfg.beer.inventory.service.domain.BeerInventory;
import guru.sfg.beer.inventory.service.repositories.BeerInventoryRepository;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.BeerOrderLineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service
public class AllocationServiceImpl implements AllocationService {

    private final BeerInventoryRepository beerInventoryRepository;

    @Override
    public Boolean allocateOrder(BeerOrderDto beerOrderDto) {
        log.debug("Allocating OrderId: " + beerOrderDto.getId());

        AtomicInteger totalOrdered = new AtomicInteger();
        AtomicInteger totalAllocated = new AtomicInteger();

        beerOrderDto.getBeerOrderLines().forEach(beerOrderLine -> {
            int orderLineQty = (beerOrderLine.getOrderQuantity() != null) ? beerOrderLine.getOrderQuantity() : 0;
            int orderLineAllocated =  (beerOrderLine.getQuantityAllocated() != null) ? beerOrderLine.getQuantityAllocated() : 0;
            if (orderLineQty > orderLineAllocated) {
                allocateBeerOrderLine(beerOrderLine);
            }
            totalOrdered.set(totalOrdered.get() + orderLineQty);
            totalAllocated.set(totalAllocated.get() + orderLineAllocated);
        });

        log.debug("Total Ordered: " + totalOrdered.get() + " Total Allocated: " + totalAllocated.get());

        return totalOrdered.get() == totalAllocated.get();
    }

    @Override
    public void deallocateOrder(BeerOrderDto beerOrderDto) {
        beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
            BeerInventory beerInventory = BeerInventory.builder()
                    .beerId(beerOrderLineDto.getBeerId())
                    .upc(beerOrderLineDto.getUpc())
                    .quantityOnHand(beerOrderLineDto.getQuantityAllocated())
                    .build();

            BeerInventory savedInventory = beerInventoryRepository.save(beerInventory);

            log.debug("Saved Inventory for beer upc: " + savedInventory.getUpc() + " inventory id: " + savedInventory.getId());
        });
    }

    private void allocateBeerOrderLine(BeerOrderLineDto beerOrderLineDto) {

        List<BeerInventory> beerInventory = beerInventoryRepository.findAllByUpc(beerOrderLineDto.getUpc());

        beerInventory.forEach(inventoryItem -> {
            int inventory = (inventoryItem.getQuantityOnHand() != null) ? inventoryItem.getQuantityOnHand() : 0;
            int orderLineQty = (beerOrderLineDto.getOrderQuantity() != null) ? beerOrderLineDto.getOrderQuantity() : 0;
            int orderLineAllocated =  (beerOrderLineDto.getQuantityAllocated() != null) ? beerOrderLineDto.getQuantityAllocated() : 0;
            int qtyToAllocate = orderLineQty - orderLineAllocated;

            if (inventory >= qtyToAllocate) { // full allocation

                inventory -= qtyToAllocate;
                beerOrderLineDto.setQuantityAllocated(orderLineQty);
                inventoryItem.setQuantityOnHand(inventory);
                beerInventoryRepository.save(inventoryItem);

            } else if (inventory > 0) { // partial allocation

                beerOrderLineDto.setQuantityAllocated(orderLineAllocated + inventory);
                inventoryItem.setQuantityOnHand(0);

                beerInventoryRepository.delete(inventoryItem);
            }
        });

    }
}
