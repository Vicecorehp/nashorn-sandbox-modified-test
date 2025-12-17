let sum = __COUNT__;

function main(count) {
    if (count < 0) {
        return -1;
    }
    for (let i = 0; i < count; i++) {
        sum += i;
    }
    return sum;
}

main();