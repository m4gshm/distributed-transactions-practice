import grpc from 'k6/net/grpc';
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

export default function () {
    ordersClient.connect(ORDER_ADDRESS, { plaintext: true });

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
            let vaild = length >= 0
            if (!vaild) {
                console.log("invalid orders length:" + length)
            } else {
                for (const order of orders) {
                    const id = order.id
                    vaild = !(id == undefined || id == null)
                    if (!vaild) {
                        console.log("invalid order id" + id)
                        break
                    }
                }
            }
            return vaild
        },
    });

    ordersClient.close();
}
