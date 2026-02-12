import grpc from 'k6/net/grpc';
import exec from 'k6/execution';
import { check } from 'k6';

const ORDER_ADDRESS = __ENV.ORDER_ADDRESS || 'localhost:9080';

const ordersClient = new grpc.Client();
ordersClient.load([
    '../proto/order/v1/api',
    '../proto',
], 'order_service.proto');

export function setup() {
}

export function teardown(data) {
}

export default function() {
    if (exec.vu.iterationInScenario == 0) {
        ordersClient.connect(ORDER_ADDRESS, { plaintext: true });
    }

    const data = {
        "page": {
            "size": 100,
            "num": 0
        }
    }

    const response = ordersClient.invoke('orders.v1.OrderService/List', data);

    check(response, {
        'status is OK': (r) => {
            return r.status === grpc.StatusOK
        },
        'Returned result': (r) => {
            const orders = r.message.orders

            const length = orders.length
            let vaild = length == 100
            if (!vaild) {
                console.log("invalid orders length:" + length)
            } else {
                for (const order of orders) {
                    const id = order.id
                    vaild = !(id == undefined || id == null)
                    if (!vaild) {
                        console.log("invalid order id" + id)
                        break
                    } else {
                        // console.log("order: " + JSON.stringify(order))
                    }
                }
            }
            return vaild
        },
    });
}
