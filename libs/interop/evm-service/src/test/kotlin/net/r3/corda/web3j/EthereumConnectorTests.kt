package net.r3.corda.web3j

import net.corda.interop.evm.*
import net.corda.interop.evm.constants.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.junit.jupiter.api.Assertions.assertEquals


class EthereumConnectorTests {

    private lateinit var mockedEVMRpc: EvmRPCCall
    private lateinit var evmConnector: EthereumConnector

    private val rpcUrl = "http://127.0.0.1:8545"
    private val mainAddress = "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"

    @BeforeEach
    fun setUp() {
        mockedEVMRpc = mock(EvmRPCCall::class.java)
        evmConnector = EthereumConnector(mockedEVMRpc)
    }

    @Test
    fun getBalance() {

        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"100000\"}"
        `when`(
            mockedEVMRpc.rpcCall(
                rpcUrl,
                ETH_GET_BALANCE,
                listOf(mainAddress, LATEST)
            )
        ).thenReturn(jsonString)
        val final = evmConnector.send<GenericResponse>(
            rpcUrl,
            ETH_GET_BALANCE,
            listOf(mainAddress, LATEST)
        )
        assertEquals("100000", final.result)
    }


    @Test
    fun getCode() {
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"0xfd2ds\"}"
        `when`(
            mockedEVMRpc.rpcCall(
                rpcUrl,
                ETH_GET_CODE,
                listOf(mainAddress, "0x1")
            )
        ).thenReturn(jsonString)
        val final = evmConnector.send<GenericResponse>(
            rpcUrl,
            ETH_GET_CODE,
            listOf(mainAddress, "0x1")
        )
        assertEquals("0xfd2ds", final.result)
    }


    @Test
    fun getChainId() {
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val jsonString = "{\"jsonrpc\":\"2.0\",\"id\":\"90.0\",\"result\":\"1337\"}"
        `when`(
            mockedEVMRpc.rpcCall(
                rpcUrl,
                GET_CHAIN_ID,
                emptyList<String>()
            )
        ).thenReturn(jsonString)
        val final = evmConnector.send<GenericResponse>(rpcUrl, GET_CHAIN_ID, emptyList<String>())
        assertEquals("1337", final.result)
    }

    @Test
    fun getBlockByNumber() {
        val jsonString = """
        {
            "id": "90.0",
            "jsonrpc": "2.0",
            "result": {
                "number": "0x1",
                "hash": "0x4c7b46fbe652b6d10cd6f68dc8516d581718bc1475d43899224ddb6651b0e5a5",
                "parentHash": "0xb21ff4855220f22371ca4412429808abf9997afbd969d67f6451a6be244a0079",
                "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
                "nonce": "0x0000000000000000",
                "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
                "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                "transactionsRoot": "0x74bf2a4cba9688e43bce3d9c972f68ef32158ad0eecefa0a47bf65c4dca3b913",
                "stateRoot": "0xc52a1cef1ce0b958e044eae061d3f7e82d05fdfee700c3c41e08a4b24ec305ee",
                "receiptsRoot": "0xe01b364aa5fb471c10231d7c1114a0c5b922d023686b04cd6e199b4d54683f9b",
                "miner": "0x0000000000000000000000000000000000000000",
                "difficulty": "0x0",
                "totalDifficulty": "0x0",
                "extraData": "0x",
                "size": "0x3e8",
                "gasLimit": "0x6691b7",
                "gasUsed": "0x2773eb",
                "timestamp": "0x656ddc60",
                "transactions": [
                    {
                        "hash": "0x1ca6e71216a4c60fc1abe92a09ff1fdbc78ab0561fb5dbf1c7d3daf90703df74",
                        "nonce": "0x0",
                        "blockHash": "0x4c7b46fbe652b6d10cd6f68dc8516d581718bc1475d43899224ddb6651b0e5a5",
                        "blockNumber": "0x1",
                        "transactionIndex": "0x0",
                        "from": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
                        "to": null,
                        "value": "0x0",
                        "gas": "0x6691b7",
                        "gasPrice": "0x4a817c800",
                        "input": "0x60806040523480156200001157600080fd5b506040518060400160405280601881526020017f4672616374696f6e616c4f776e657273686970546f6b656e000000000000000081525062000059816200007760201b60201c565b506200007160036200008c60201b620008d21760201c565b62000403565b80600290816200008891906200031c565b5050565b6001816000016000828254019250508190555050565b600081519050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b600060028204905060018216806200012457607f821691505b6020821081036200013a5762000139620000dc565b5b50919050565b60008190508160005260206000209050919050565b60006020601f8301049050919050565b600082821b905092915050565b600060088302620001a47fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8262000165565b620001b0868362000165565b95508019841693508086168417925050509392505050565b6000819050919050565b6000819050919050565b6000620001fd620001f7620001f184620001c8565b620001d2565b620001c8565b9050919050565b6000819050919050565b6200021983620001dc565b62000231620002288262000204565b84845462000172565b825550505050565b600090565b6200024862000239565b620002558184846200020e565b505050565b5b818110156200027d57620002716000826200023e565b6001810190506200025b565b5050565b601f821115620002cc57620002968162000140565b620002a18462000155565b81016020851015620002b1578190505b620002c9620002c08562000155565b8301826200025a565b50505b505050565b600082821c905092915050565b6000620002f160001984600802620002d1565b1980831691505092915050565b60006200030c8383620002de565b9150826002028217905092915050565b6200032782620000a2565b67ffffffffffffffff811115620003435762000342620000ad565b5b6200034f82546200010b565b6200035c82828562000281565b600060209050601f8311600181146200039457600084156200037f578287015190505b6200038b8582620002fe565b865550620003fb565b601f198416620003a48662000140565b60005b82811015620003ce57848901518255600182019150602085019450602081019050620003a7565b86831015620003ee5784890151620003ea601f891682620002de565b8355505b6001600288020188555050505b505050505050565b612cfd80620004136000396000f3fe608060405234801561001057600080fd5b50600436106100a85760003560e01c80634e1273f4116100715780634e1273f4146101755780635c21bc2c146101a557806364e1df7a146101d5578063a22cb46514610205578063e985e9c514610221578063f242432a14610251576100a8565b8062fdd58e146100ad57806301ffc9a7146100dd5780630e89341c1461010d5780632eb2c2d61461013d57806334d27c5f14610159575b600080fd5b6100c760048036038101906100c29190611743565b61026d565b6040516100d49190611792565b60405180910390f35b6100f760048036038101906100f29190611805565b610335565b604051610104919061184d565b60405180910390f35b61012760048036038101906101229190611868565b610417565b6040516101349190611925565b60405180910390f35b61015760048036038101906101529190611b44565b6104ab565b005b610173600480360381019061016e9190611c13565b61054c565b005b61018f600480360381019061018a9190611d29565b61056e565b60405161019c9190611e5f565b60405180910390f35b6101bf60048036038101906101ba9190611f22565b610687565b6040516101cc9190611792565b60405180910390f35b6101ef60048036038101906101ea9190611868565b6106e7565b6040516101fc9190611925565b60405180910390f35b61021f600480360381019061021a9190611fbd565b610787565b005b61023b60048036038101906102369190611ffd565b61079d565b604051610248919061184d565b60405180910390f35b61026b6004803603810190610266919061203d565b610831565b005b60008073ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff16036102dd576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016102d490612146565b60405180910390fd5b60008083815260200190815260200160002060008473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054905092915050565b60007fd9b67a26000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916827bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916148061040057507f0e89341c000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916827bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916145b80610410575061040f826108e8565b5b9050919050565b60606002805461042690612195565b80601f016020809104026020016040519081016040528092919081815260200182805461045290612195565b801561049f5780601f106104745761010080835404028352916020019161049f565b820191906000526020600020905b81548152906001019060200180831161048257829003601f168201915b50505050509050919050565b6104b3610952565b73ffffffffffffffffffffffffffffffffffffffff168573ffffffffffffffffffffffffffffffffffffffff1614806104f957506104f8856104f3610952565b61079d565b5b610538576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161052f90612238565b60405180910390fd5b610545858585858561095a565b5050505050565b610569838360018460405180602001604052806000815250610831565b505050565b606081518351146105b4576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016105ab906122ca565b60405180910390fd5b6000835167ffffffffffffffff8111156105d1576105d061194c565b5b6040519080825280602002602001820160405280156105ff5781602001602082028036833780820191505090505b50905060005b845181101561067c5761064c858281518110610624576106236122ea565b5b602002602001015185838151811061063f5761063e6122ea565b5b602002602001015161026d565b82828151811061065f5761065e6122ea565b5b6020026020010181815250508061067590612348565b9050610605565b508091505092915050565b6000806106946003610c7b565b90506106b185828660405180602001604052806000815250610c89565b826004600083815260200190815260200160002090816106d1919061253c565b506106dc60036108d2565b809150509392505050565b6004602052806000526040600020600091509050805461070690612195565b80601f016020809104026020016040519081016040528092919081815260200182805461073290612195565b801561077f5780601f106107545761010080835404028352916020019161077f565b820191906000526020600020905b81548152906001019060200180831161076257829003601f168201915b505050505081565b610799610792610952565b8383610e39565b5050565b6000600160008473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060009054906101000a900460ff16905092915050565b610839610952565b73ffffffffffffffffffffffffffffffffffffffff168573ffffffffffffffffffffffffffffffffffffffff16148061087f575061087e85610879610952565b61079d565b5b6108be576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016108b590612238565b60405180910390fd5b6108cb8585858585610fa5565b5050505050565b6001816000016000828254019250508190555050565b60007f01ffc9a7000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916827bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916149050919050565b600033905090565b815183511461099e576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161099590612680565b60405180910390fd5b600073ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff1603610a0d576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610a0490612712565b60405180910390fd5b6000610a17610952565b9050610a27818787878787611240565b60005b8451811015610bd8576000858281518110610a4857610a476122ea565b5b602002602001015190506000858381518110610a6757610a666122ea565b5b60200260200101519050600080600084815260200190815260200160002060008b73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054905081811015610b08576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610aff906127a4565b60405180910390fd5b81810360008085815260200190815260200160002060008c73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020819055508160008085815260200190815260200160002060008b73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000206000828254610bbd91906127c4565b9250508190555050505080610bd190612348565b9050610a2a565b508473ffffffffffffffffffffffffffffffffffffffff168673ffffffffffffffffffffffffffffffffffffffff168273ffffffffffffffffffffffffffffffffffffffff167f4a39dc06d4c0dbc64b70af90fd698a233a518aa5d07e595d983b8c0526c8f7fb8787604051610c4f9291906127f8565b60405180910390a4610c65818787878787611248565b610c73818787878787611250565b505050505050565b600081600001549050919050565b600073ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff1603610cf8576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610cef906128a1565b60405180910390fd5b6000610d02610952565b90506000610d0f85611427565b90506000610d1c85611427565b9050610d2d83600089858589611240565b8460008088815260200190815260200160002060008973ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000206000828254610d8c91906127c4565b925050819055508673ffffffffffffffffffffffffffffffffffffffff16600073ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff167fc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f628989604051610e0a9291906128c1565b60405180910390a4610e2183600089858589611248565b610e30836000898989896114a1565b50505050505050565b8173ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff1603610ea7576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610e9e9061295c565b60405180910390fd5b80600160008573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060008473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060006101000a81548160ff0219169083151502179055508173ffffffffffffffffffffffffffffffffffffffff168373ffffffffffffffffffffffffffffffffffffffff167f17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c3183604051610f98919061184d565b60405180910390a3505050565b600073ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff1603611014576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161100b90612712565b60405180910390fd5b600061101e610952565b9050600061102b85611427565b9050600061103885611427565b9050611048838989858589611240565b600080600088815260200190815260200160002060008a73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020549050858110156110df576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016110d6906127a4565b60405180910390fd5b85810360008089815260200190815260200160002060008b73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020819055508560008089815260200190815260200160002060008a73ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020600082825461119491906127c4565b925050819055508773ffffffffffffffffffffffffffffffffffffffff168973ffffffffffffffffffffffffffffffffffffffff168573ffffffffffffffffffffffffffffffffffffffff167fc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f628a8a6040516112119291906128c1565b60405180910390a4611227848a8a86868a611248565b611235848a8a8a8a8a6114a1565b505050505050505050565b505050505050565b505050505050565b61126f8473ffffffffffffffffffffffffffffffffffffffff16611678565b1561141f578373ffffffffffffffffffffffffffffffffffffffff1663bc197c8187878686866040518663ffffffff1660e01b81526004016112b59594939291906129e0565b6020604051808303816000875af19250505080156112f157506040513d601f19601f820116820180604052508101906112ee9190612a5d565b60015b611396576112fd612a97565b806308c379a0036113595750611311612ab9565b8061131c575061135b565b806040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016113509190611925565b60405180910390fd5b505b6040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161138d90612bbb565b60405180910390fd5b63bc197c8160e01b7bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916817bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19161461141d576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161141490612c4d565b60405180910390fd5b505b505050505050565b60606000600167ffffffffffffffff8111156114465761144561194c565b5b6040519080825280602002602001820160405280156114745781602001602082028036833780820191505090505b509050828160008151811061148c5761148b6122ea565b5b60200260200101818152505080915050919050565b6114c08473ffffffffffffffffffffffffffffffffffffffff16611678565b15611670578373ffffffffffffffffffffffffffffffffffffffff1663f23a6e6187878686866040518663ffffffff1660e01b8152600401611506959493929190612c6d565b6020604051808303816000875af192505050801561154257506040513d601f19601f8201168201806040525081019061153f9190612a5d565b60015b6115e75761154e612a97565b806308c379a0036115aa5750611562612ab9565b8061156d57506115ac565b806040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016115a19190611925565b60405180910390fd5b505b6040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016115de90612bbb565b60405180910390fd5b63f23a6e6160e01b7bffffffffffffffffffffffffffffffffffffffffffffffffffffffff1916817bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19161461166e576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161166590612c4d565b60405180910390fd5b505b505050505050565b6000808273ffffffffffffffffffffffffffffffffffffffff163b119050919050565b6000604051905090565b600080fd5b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006116da826116af565b9050919050565b6116ea816116cf565b81146116f557600080fd5b50565b600081359050611707816116e1565b92915050565b6000819050919050565b6117208161170d565b811461172b57600080fd5b50565b60008135905061173d81611717565b92915050565b6000806040838503121561175a576117596116a5565b5b6000611768858286016116f8565b92505060206117798582860161172e565b9150509250929050565b61178c8161170d565b82525050565b60006020820190506117a76000830184611783565b92915050565b60007fffffffff0000000000000000000000000000000000000000000000000000000082169050919050565b6117e2816117ad565b81146117ed57600080fd5b50565b6000813590506117ff816117d9565b92915050565b60006020828403121561181b5761181a6116a5565b5b6000611829848285016117f0565b91505092915050565b60008115159050919050565b61184781611832565b82525050565b6000602082019050611862600083018461183e565b92915050565b60006020828403121561187e5761187d6116a5565b5b600061188c8482850161172e565b91505092915050565b600081519050919050565b600082825260208201905092915050565b60005b838110156118cf5780820151818401526020810190506118b4565b60008484015250505050565b6000601f19601f8301169050919050565b60006118f782611895565b61190181856118a0565b93506119118185602086016118b1565b61191a816118db565b840191505092915050565b6000602082019050818103600083015261193f81846118ec565b905092915050565b600080fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b611984826118db565b810181811067ffffffffffffffff821117156119a3576119a261194c565b5b80604052505050565b60006119b661169b565b90506119c2828261197b565b919050565b600067ffffffffffffffff8211156119e2576119e161194c565b5b602082029050602081019050919050565b600080fd5b6000611a0b611a06846119c7565b6119ac565b90508083825260208201905060208402830185811115611a2e57611a2d6119f3565b5b835b81811015611a575780611a43888261172e565b845260208401935050602081019050611a30565b5050509392505050565b600082601f830112611a7657611a75611947565b5b8135611a868482602086016119f8565b91505092915050565b600080fd5b600067ffffffffffffffff821115611aaf57611aae61194c565b5b611ab8826118db565b9050602081019050919050565b82818337600083830152505050565b6000611ae7611ae284611a94565b6119ac565b905082815260208101848484011115611b0357611b02611a8f565b5b611b0e848285611ac5565b509392505050565b600082601f830112611b2b57611b2a611947565b5b8135611b3b848260208601611ad4565b91505092915050565b600080600080600060a08688031215611b6057611b5f6116a5565b5b6000611b6e888289016116f8565b9550506020611b7f888289016116f8565b945050604086013567ffffffffffffffff811115611ba057611b9f6116aa565b5b611bac88828901611a61565b935050606086013567ffffffffffffffff811115611bcd57611bcc6116aa565b5b611bd988828901611a61565b925050608086013567ffffffffffffffff811115611bfa57611bf96116aa565b5b611c0688828901611b16565b9150509295509295909350565b600080600060608486031215611c2c57611c2b6116a5565b5b6000611c3a868287016116f8565b9350506020611c4b868287016116f8565b9250506040611c5c8682870161172e565b9150509250925092565b600067ffffffffffffffff821115611c8157611c8061194c565b5b602082029050602081019050919050565b6000611ca5611ca084611c66565b6119ac565b90508083825260208201905060208402830185811115611cc857611cc76119f3565b5b835b81811015611cf15780611cdd88826116f8565b845260208401935050602081019050611cca565b5050509392505050565b600082601f830112611d1057611d0f611947565b5b8135611d20848260208601611c92565b91505092915050565b60008060408385031215611d4057611d3f6116a5565b5b600083013567ffffffffffffffff811115611d5e57611d5d6116aa565b5b611d6a85828601611cfb565b925050602083013567ffffffffffffffff811115611d8b57611d8a6116aa565b5b611d9785828601611a61565b9150509250929050565b600081519050919050565b600082825260208201905092915050565b6000819050602082019050919050565b611dd68161170d565b82525050565b6000611de88383611dcd565b60208301905092915050565b6000602082019050919050565b6000611e0c82611da1565b611e168185611dac565b9350611e2183611dbd565b8060005b83811015611e52578151611e398882611ddc565b9750611e4483611df4565b925050600181019050611e25565b5085935050505092915050565b60006020820190508181036000830152611e798184611e01565b905092915050565b600067ffffffffffffffff821115611e9c57611e9b61194c565b5b611ea5826118db565b9050602081019050919050565b6000611ec5611ec084611e81565b6119ac565b905082815260208101848484011115611ee157611ee0611a8f565b5b611eec848285611ac5565b509392505050565b600082601f830112611f0957611f08611947565b5b8135611f19848260208601611eb2565b91505092915050565b600080600060608486031215611f3b57611f3a6116a5565b5b6000611f49868287016116f8565b9350506020611f5a8682870161172e565b925050604084013567ffffffffffffffff811115611f7b57611f7a6116aa565b5b611f8786828701611ef4565b9150509250925092565b611f9a81611832565b8114611fa557600080fd5b50565b600081359050611fb781611f91565b92915050565b60008060408385031215611fd457611fd36116a5565b5b6000611fe2858286016116f8565b9250506020611ff385828601611fa8565b9150509250929050565b60008060408385031215612014576120136116a5565b5b6000612022858286016116f8565b9250506020612033858286016116f8565b9150509250929050565b600080600080600060a08688031215612059576120586116a5565b5b6000612067888289016116f8565b9550506020612078888289016116f8565b94505060406120898882890161172e565b935050606061209a8882890161172e565b925050608086013567ffffffffffffffff8111156120bb576120ba6116aa565b5b6120c788828901611b16565b9150509295509295909350565b7f455243313135353a2061646472657373207a65726f206973206e6f742061207660008201527f616c6964206f776e657200000000000000000000000000000000000000000000602082015250565b6000612130602a836118a0565b915061213b826120d4565b604082019050919050565b6000602082019050818103600083015261215f81612123565b9050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b600060028204905060018216806121ad57607f821691505b6020821081036121c0576121bf612166565b5b50919050565b7f455243313135353a2063616c6c6572206973206e6f7420746f6b656e206f776e60008201527f6572206f7220617070726f766564000000000000000000000000000000000000602082015250565b6000612222602e836118a0565b915061222d826121c6565b604082019050919050565b6000602082019050818103600083015261225181612215565b9050919050565b7f455243313135353a206163636f756e747320616e6420696473206c656e67746860008201527f206d69736d617463680000000000000000000000000000000000000000000000602082015250565b60006122b46029836118a0565b91506122bf82612258565b604082019050919050565b600060208201905081810360008301526122e3816122a7565b9050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603260045260246000fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b60006123538261170d565b91507fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff820361238557612384612319565b5b600182019050919050565b60008190508160005260206000209050919050565b60006020601f8301049050919050565b600082821b905092915050565b6000600883026123f27fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff826123b5565b6123fc86836123b5565b95508019841693508086168417925050509392505050565b6000819050919050565b600061243961243461242f8461170d565b612414565b61170d565b9050919050565b6000819050919050565b6124538361241e565b61246761245f82612440565b8484546123c2565b825550505050565b600090565b61247c61246f565b61248781848461244a565b505050565b5b818110156124ab576124a0600082612474565b60018101905061248d565b5050565b601f8211156124f0576124c181612390565b6124ca846123a5565b810160208510156124d9578190505b6124ed6124e5856123a5565b83018261248c565b50505b505050565b600082821c905092915050565b6000612513600019846008026124f5565b1980831691505092915050565b600061252c8383612502565b9150826002028217905092915050565b61254582611895565b67ffffffffffffffff81111561255e5761255d61194c565b5b6125688254612195565b6125738282856124af565b600060209050601f8311600181146125a65760008415612594578287015190505b61259e8582612520565b865550612606565b601f1984166125b486612390565b60005b828110156125dc578489015182556001820191506020850194506020810190506125b7565b868310156125f957848901516125f5601f891682612502565b8355505b6001600288020188555050505b505050505050565b7f455243313135353a2069647320616e6420616d6f756e7473206c656e6774682060008201527f6d69736d61746368000000000000000000000000000000000000000000000000602082015250565b600061266a6028836118a0565b91506126758261260e565b604082019050919050565b600060208201905081810360008301526126998161265d565b9050919050565b7f455243313135353a207472616e7366657220746f20746865207a65726f20616460008201527f6472657373000000000000000000000000000000000000000000000000000000602082015250565b60006126fc6025836118a0565b9150612707826126a0565b604082019050919050565b6000602082019050818103600083015261272b816126ef565b9050919050565b7f455243313135353a20696e73756666696369656e742062616c616e636520666f60008201527f72207472616e7366657200000000000000000000000000000000000000000000602082015250565b600061278e602a836118a0565b915061279982612732565b604082019050919050565b600060208201905081810360008301526127bd81612781565b9050919050565b60006127cf8261170d565b91506127da8361170d565b92508282019050808211156127f2576127f1612319565b5b92915050565b600060408201905081810360008301526128128185611e01565b905081810360208301526128268184611e01565b90509392505050565b7f455243313135353a206d696e7420746f20746865207a65726f2061646472657360008201527f7300000000000000000000000000000000000000000000000000000000000000602082015250565b600061288b6021836118a0565b91506128968261282f565b604082019050919050565b600060208201905081810360008301526128ba8161287e565b9050919050565b60006040820190506128d66000830185611783565b6128e36020830184611783565b9392505050565b7f455243313135353a2073657474696e6720617070726f76616c2073746174757360008201527f20666f722073656c660000000000000000000000000000000000000000000000602082015250565b60006129466029836118a0565b9150612951826128ea565b604082019050919050565b6000602082019050818103600083015261297581612939565b9050919050565b612985816116cf565b82525050565b600081519050919050565b600082825260208201905092915050565b60006129b28261298b565b6129bc8185612996565b93506129cc8185602086016118b1565b6129d5816118db565b840191505092915050565b600060a0820190506129f5600083018861297c565b612a02602083018761297c565b8181036040830152612a148186611e01565b90508181036060830152612a288185611e01565b90508181036080830152612a3c81846129a7565b90509695505050505050565b600081519050612a57816117d9565b92915050565b600060208284031215612a7357612a726116a5565b5b6000612a8184828501612a48565b91505092915050565b60008160e01c9050919050565b600060033d1115612ab65760046000803e612ab3600051612a8a565b90505b90565b600060443d10612b4657612acb61169b565b60043d036004823e80513d602482011167ffffffffffffffff82111715612af3575050612b46565b808201805167ffffffffffffffff811115612b115750505050612b46565b80602083010160043d038501811115612b2e575050505050612b46565b612b3d8260200185018661197b565b82955050505050505b90565b7f455243313135353a207472616e7366657220746f206e6f6e2d4552433131353560008201527f526563656976657220696d706c656d656e746572000000000000000000000000602082015250565b6000612ba56034836118a0565b9150612bb082612b49565b604082019050919050565b60006020820190508181036000830152612bd481612b98565b9050919050565b7f455243313135353a204552433131353552656365697665722072656a6563746560008201527f6420746f6b656e73000000000000000000000000000000000000000000000000602082015250565b6000612c376028836118a0565b9150612c4282612bdb565b604082019050919050565b60006020820190508181036000830152612c6681612c2a565b9050919050565b600060a082019050612c82600083018861297c565b612c8f602083018761297c565b612c9c6040830186611783565b612ca96060830185611783565b8181036080830152612cbb81846129a7565b9050969550505050505056fea26469706673582212201b559cae4f150ac263777336108d5f0db0111bbe9b9e9eadd0f6fb127dd6b72164736f6c63430008120033",
                        "v": "0xa96",
                        "r": "0x717547d3782e959e1336ba5e066bb24373d83fc8a07be5b186ff42cc4e7c231b",
                        "s": "0x6202af8bfd7d37374997a759f817da345c9f370d86c9d8cb2bb1e52073afc1b9"
                    }
                ],
                "uncles": []
            }
        }
    """.trimIndent()
        val mockedEVMRpc = mock(EvmRPCCall::class.java)
        val evmConnector = EthereumConnector(mockedEVMRpc)
        val blockNumber = 1
        val fullTransactionObjects = true

        `when`(mockedEVMRpc.rpcCall(rpcUrl, ETH_GET_BLOCK_BY_NUMBER, listOf(blockNumber, fullTransactionObjects))).thenReturn(jsonString)

        val final = evmConnector.send<EthereumBlock>(rpcUrl, ETH_GET_BLOCK_BY_NUMBER, listOf(blockNumber, fullTransactionObjects))
        println(final)

    }

}