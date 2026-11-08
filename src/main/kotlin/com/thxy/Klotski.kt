package com.thxy

/**
 * 不可变的 n×n 华容道模型。
 *
 * 棋盘使用二维列表存储，数字按行优先排列，0 表示空白格。
 * 所有坐标均从 1 开始，格式为 `(x, y)`，其中 x 是列、y 是行。
 *
 * 除用于调试输出的 [toString]、供 UI 读取棋盘的 [snapshot]，以及
 * [up]、[down]、[left]、[right]、[random]、[isFinish] 这些操作外，
 * 坐标读取和交换方法均为 private，从而由访问权限保证外部无法传入
 * 越界坐标。
 *
 * @property klotski 棋盘的二维数字列表，实例创建后不会被修改。
 */
class Klotski private constructor(private val klotski: List<List<Int>>) {

    /**
     * 创建一个 n×n 的已完成华容道棋盘。
     *
     * @param n 棋盘边长，必须大于 0。
     * @throws IllegalArgumentException 当 [n] 不大于 0 时抛出。
     */
    constructor(n: Int) : this(buildKlotski(n))

    /**
     * 华容道棋盘构建相关的伴生工具。
     */
    private companion object {
        /**
         * 构建一个 n×n 的已完成华容道棋盘。
         *
         * 数字 1 到 n²-1 按行优先顺序排列，空白格 0 位于右下角。
         *
         * @param n 棋盘边长，必须大于 0。
         * @return 新构建的二维棋盘列表。
         * @throws IllegalArgumentException 当 [n] 不大于 0 时抛出。
         */
        private fun buildKlotski(n: Int): List<List<Int>> {
            require(n > 0) { "棋盘边长必须大于 0：$n" }
            return List(n) { y ->
                List(n) { x ->
                    if (x == n - 1 && y == n - 1) 0
                    else y * n + x + 1
                }
            }
        }
    }

    /** 棋盘边长，仅供类内部使用。 */
    private val size = klotski.size

    /**
     * 空白格 0 的坐标，格式为 `(x, y)`，从 1 开始。
     *
     * 仅供类内部使用。
     */
    private val zero: Pair<Int, Int> = klotski.flatMapIndexed { y, row ->
        row.mapIndexedNotNull { x, value ->
            if (value == 0) (x + 1) to (y + 1) else null
        }
    }.single()

    /**
     * 返回棋盘的字符串表示，便于调试输出。
     *
     * 该方法覆盖自 `Any.toString()`，受 Kotlin 可见性规则限制保持 public。
     *
     * @return 底层二维列表的字符串形式。
     */
    override fun toString() = klotski.toString()

    /**
     * 判断当前华容道棋盘是否已经完成。
     *
     * 完成状态定义为数字按行优先顺序排列，且空白格位于右下角。
     *
     * @return 已完成返回 `true`，否则返回 `false`。
     */
    fun isFinish(): Boolean {
        var expected = 1
        val total = size * size
        for (row in klotski) {
            for (value in row) {
                if (expected == total) return value == 0
                if (value != expected) return false
                expected++
            }
        }
        return false
    }

    /**
     * 返回按行优先排列的棋盘副本。
     *
     * 数组长度为 n×n，0 表示空白格。该副本供 UI 渲染使用，修改它不会
     * 影响当前棋盘。
     *
     * @return 棋盘数字的行优先副本。
     */
    fun snapshot(): IntArray = IntArray(size * size) { index ->
        klotski[index / size][index % size]
    }

    /**
     * 获取第 [x] 列。
     *
     * 仅供类内部使用，调用方必须保证 [x] 在 `1..size` 范围内。
     * 返回的列表长度为 `size + 1`：索引 0 固定为 `-1` 作为哨兵，
     * 索引 `y`（1..size）对应坐标 `(x, y)` 的值。
     *
     * @param x 列坐标，范围为 `1..size`。
     * @return 带哨兵值的列数据。
     */
    private operator fun get(x: Int): List<Int> = List(size + 1) { y ->
        if (y == 0) -1 else klotski[y - 1][x - 1]
    }

    /**
     * 交换坐标 [a] 和 [b] 上的数字，并返回新棋盘。
     *
     * 仅供类内部使用，调用方必须保证 [a]、[b] 都是棋盘内的合法坐标。
     * 当前实例保持不变。
     *
     * @param a 第一个坐标，格式为 `(x, y)`，从 1 开始。
     * @param b 第二个坐标，格式为 `(x, y)`，从 1 开始。
     * @return 交换后的新棋盘。
     */
    private fun swap(a: Pair<Int, Int>, b: Pair<Int, Int>): Klotski = Klotski(
        klotski.mapIndexed { y, xList ->
            xList.mapIndexed { x, value ->
                when (x + 1 to y + 1) {
                    a -> this[b.first][b.second]
                    b -> this[a.first][a.second]
                    else -> value
                }
            }
        })

    /**
     * 将空白格下方的数字块向上移动一格。
     *
     * 空白格会相应向下移动；若空白格已经在最底部，则没有可移动的数字块，
     * 返回当前实例。
     *
     * @return 移动后的新棋盘；无法移动时返回当前实例。
     */
    fun up() = if (zero.second == size) this else swap(zero, zero.first to zero.second + 1)

    /**
     * 将空白格上方的数字块向下移动一格。
     *
     * 空白格会相应向上移动；若空白格已经在最顶部，则没有可移动的数字块，
     * 返回当前实例。
     *
     * @return 移动后的新棋盘；无法移动时返回当前实例。
     */
    fun down() = if (zero.second == 1) this else swap(zero, zero.first to zero.second - 1)

    /**
     * 将空白格右侧的数字块向左移动一格。
     *
     * 空白格会相应向右移动；若空白格已经在最右侧，则没有可移动的数字块，
     * 返回当前实例。
     *
     * @return 移动后的新棋盘；无法移动时返回当前实例。
     */
    fun left() = if (zero.first == size) this else swap(zero.first + 1 to zero.second, zero)

    /**
     * 将空白格左侧的数字块向右移动一格。
     *
     * 空白格会相应向左移动；若空白格已经在最左侧，则没有可移动的数字块，
     * 返回当前实例。
     *
     * @return 移动后的新棋盘；无法移动时返回当前实例。
     */
    fun right() = if (zero.first == 1) this else swap(zero.first - 1 to zero.second, zero)

    /**
     * 生成一个随机且必定可解的华容道棋盘。
     *
     * 空白格固定在右下角，数字使用随机排列；通过逆序数校验保证排列的
     * 奇偶性满足可解条件。该算法的时间复杂度为 O(n² log n)，可以用于
     * 最大 215×215 的棋盘。
     *
     * @return 随机打乱后的新棋盘。
     */
    fun random(): Klotski {
        if (size == 1) return this

        val numbers = IntArray(size * size - 1) { it + 1 }
        numbers.shuffle()

        // 空白格在右下角时，数字排列的逆序数为偶数即可解。
        if (countInversions(numbers) % 2L != 0L) {
            val first = numbers[0]
            numbers[0] = numbers[1]
            numbers[1] = first
        }

        val result = boardFromNumbers(numbers)
        if (!result.isFinish()) return result

        // 对前三个数字做一次偶置换，避免随机结果恰好是完成状态。
        val first = numbers[0]
        numbers[0] = numbers[1]
        numbers[1] = numbers[2]
        numbers[2] = first
        return boardFromNumbers(numbers)
    }

    /**
     * 根据按行优先排列的数字构建棋盘，空白格放在右下角。
     *
     * @param numbers 长度为 n²-1 的数字数组，包含 1..n²-1。
     * @return 由这些数字构成的棋盘。
     */
    private fun boardFromNumbers(numbers: IntArray): Klotski {
        val lastIndex = size * size - 1
        return Klotski(
            List(size) { y ->
                List(size) { x ->
                    val index = y * size + x
                    if (index == lastIndex) 0 else numbers[index]
                }
            }
        )
    }

    /**
     * 使用树状数组统计数字排列的逆序数。
     *
     * 使用 `foldRight` 和惰性索引序列完成迭代，不显式声明可变累加器；
     * 树状数组本身仍然是内部可变的。
     *
     * @param numbers 长度为 n²-1、由 1..n²-1 组成的排列。
     * @return 该排列的逆序对数。
     */
    private fun countInversions(numbers: IntArray): Long {
        val tree = IntArray(numbers.size + 1)

        return numbers.foldRight(0L) { value, inversions ->
            val smaller = generateSequence(value) { index ->
                index - (index and -index)
            }.takeWhile { index -> index > 0 }
                .sumOf { index -> tree[index].toLong() }

            generateSequence(value) { index ->
                index + (index and -index)
            }.takeWhile { index -> index < tree.size }
                .forEach { index -> tree[index]++ }

            inversions + smaller
        }
    }
}
