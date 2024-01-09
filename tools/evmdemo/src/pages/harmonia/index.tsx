import React from "react";
import {
  CommitWithTokenFlow,
  IssueGenericAssetFlow,
  RequestLockByEventFlow,
  SignDraftTransactionByIdFlow,
  claimCommitment,
  unlockAssetFlow,
  RequestBlockHeadersProofsFlow,
} from "@/api/gateway";
import { Web3 } from "web3";
import { abi } from "@/assets/erc20";
import { ethers } from "ethers";
import toast, { Toaster } from "react-hot-toast";
import Layout from "@/components/Layout";
import { useSpring, animated } from "@react-spring/web";

const Harmonia = () => {
  const [transactionId, setTransactionId] = React.useState("");
  const [assetId, setAssetId] = React.useState("");
  const [owner, setOwner] = React.useState("");
  const [lockTransactionId, setLockTransactionId] = React.useState("");
  const [blockNumber, setBlockNumber] = React.useState(0);
  const [transactionIndex, setTransactionIndex] = React.useState(0);

  const [evmBalanceAccount1, setEvmBalanceAccount1] = React.useState(0);
  const [evmBalanceAccount2, setEvmBalanceAccount2] = React.useState(0);
  const [swapContractBalance, setSwapContractBalance] = React.useState(0);

  const [highlighted, setHighlighted] = React.useState<String[]>([]);

  const [step, setStep] = React.useState(0);

  const walletAddress1 = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
  const walletAddress2 = "0x627306090abaB3A6e1400e9345bC60c78a8BEf57";

  const holdingId1 = "2CD775D033E7";
  const holdingId2 = "98BA3D01227D";

  const StringToHex = (str) => {
    const web3 = new Web3();
    return web3.utils.fromAscii(str);
  };

  const RPC_URL = process.env.DEMO_RPC_URL || "http://host.docker.internal:8545";



  const GetErc20BalanceOf = async () => {
    const provider = new ethers.providers.JsonRpcProvider(
      `http://127.0.0.1:8545`
    );

    // Connect to the ERC-20 contract
    const contract = new ethers.Contract(
      process.env.ERC20_ADDRESS,
      abi,
      provider
    );

    // Get the balance of the specified address
    const balance = await contract.balanceOf(walletAddress1);

    // Print the balance
    console.log(`Balance of ${walletAddress1}: ${balance.toString()}`);
    setEvmBalanceAccount1(balance.toString());

    const balance2 = await contract.balanceOf(walletAddress2);
    console.log(`Balance of ${walletAddress2}: ${balance2.toString()}`);
    setEvmBalanceAccount2(balance2.toString());

    const balance3 = await contract.balanceOf(process.env.SWAP_VAULT_ADDRESS);
    console.log(
      `Balance of ${process.env.SWAP_VAULT_ADDRESS}: ${balance3.toString()}`
    );
    setSwapContractBalance(balance3.toString());
   };

  // rrandom string funciton
  const randomString = (length) => {
    let result = "";
    const characters =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    const charactersLength = characters.length;

    for (let i = 0; i < length; i++) {
      result += characters.charAt(Math.floor(Math.random() * charactersLength));
    }

    return result;
  };

  const issueGenericAsset = async () => {
    setHighlighted(["ownerOfCordaAsset"]);
    const assetId = randomString(10);
    const res = await IssueGenericAssetFlow(holdingId1, randomString(10));
    setAssetId(assetId);
    setOwner("Bob");
    setTransactionId(res);
    setStep(1);
  };

  const requestLockByEvent = async () => {
    setHighlighted([]);
    const output = await RequestLockByEventFlow(
      holdingId1,
      transactionId,
      "com.r3.corda.demo.swaps.contracts.swap.AssetState",
      "CN=Eve, OU=Application, O=NordVPN, L=Athens, C=GR",
      1,
      1337,
      process.env.SWAP_VAULT_ADDRESS,
      "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
      "0x627306090abaB3A6e1400e9345bC60c78a8BEf57",
      process.env.ERC20_ADDRESS,
      100,
      0
    );
    console.log("output: ", output);
    setLockTransactionId(output);
    setStep(2);
  };

  const commitWithTokenFlow = async () => {
    setHighlighted([...["aliceEvmBalance", "swapContractBalance"]]);
    const output2 = await CommitWithTokenFlow(
      holdingId2,
      lockTransactionId,
      RPC_URL,
      process.env.ERC20_ADDRESS,
      "0x627306090abaB3A6e1400e9345bC60c78a8BEf57",
      100,
      1,
      ["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"],
      "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
      process.env.SWAP_VAULT_ADDRESS,
      ""
    );

    await GetErc20BalanceOf();
    console.log("output2: ", output2);
    setStep(3);
  };

  const signDraftTransactionById = async () => {
    setHighlighted([]);
    const draftTxOutput = await SignDraftTransactionByIdFlow(
      holdingId1,
      lockTransactionId
    );
    console.log("draftTxOutput: ", draftTxOutput);
    setStep(4);
  };

  const claimCommitmentFlow = async () => {
    setHighlighted(["bobEvmBalance", "swapContractBalance"]);
    const commitment = await claimCommitment(
      holdingId2,
      lockTransactionId,
      RPC_URL,
      ["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"],
      process.env.SWAP_VAULT_ADDRESS,
      ""
    );
    console.log("commitment: ", commitment);
    const jsonCommitment = JSON.parse(commitment);
    const transactionReceipt = jsonCommitment.transactionReceipt;
    const blockNumber = transactionReceipt.blockNumber;
    const transactionIndex = transactionReceipt.transactionIndex;

    setBlockNumber(blockNumber);
    setTransactionIndex(transactionIndex);
    await GetErc20BalanceOf();
    setStep(5);
  };

  const unlockAsset = async () => {
    setHighlighted(["ownerOfCordaAsset"]);
    const unlockAssetOutput = await unlockAssetFlow(
      holdingId2,
      lockTransactionId,
      blockNumber,
      transactionIndex
    );
    console.log("unlockAssetOutput: ", unlockAssetOutput);
    setOwner("Alice");
  };

  const RequestBlockProofs = async () => {
    setHighlighted([]);
    await RequestBlockHeadersProofsFlow(
      holdingId2,
      blockNumber,
      ["CN=Testing, OU=Application, O=R3, L=London, C=GB"],
      RPC_URL
    );
    setStep(6);
  };

  const reset = async () => {
    setTransactionId("");
    setAssetId("");
    setOwner("");
    setLockTransactionId("");
    setBlockNumber(0);
    setTransactionIndex(0);
    setHighlighted([]);

    setStep(0);
  };

  const getInfo = async () => {
    const output = await GetErc20BalanceOf();
    console.log("output: ", output);
  };
  React.useEffect(() => {
    getInfo();
  }, []);

  const aliceEvmBalanceProps = useSpring({
    fontSize: highlighted.includes("aliceEvmBalance") ? 25 : 15,
    fontFamily: "Roboto",
    // backgroundColor: highlighted.includes("aliceEvmBalance") ? "yellow" : "transparent",
    color: highlighted.includes("aliceEvmBalance") ? "red" : "black",
  });

  const bobEvmBalanceProps = useSpring({
    fontSize: highlighted.includes("bobEvmBalance") ? 25 : 15,
    fontFamily: "Roboto",
    color: highlighted.includes("bobEvmBalance") ? "red" : "black",

    // backgroundColor: highlighted.includes("bobEvmBalance") ? "yellow" : "transparent",
  });

  const swapContractBalanceProps = useSpring({
    fontSize: highlighted.includes("swapContractBalance") ? 25 : 15,
    fontFamily: "Roboto",
    color: highlighted.includes("swapContractBalance") ? "red" : "black",

    // backgroundColor: highlighted.includes("swapContractBalance") ? "yellow" : "transparent",
  });

  const ownerOfCordaAssetProps = useSpring({
    fontSize: highlighted.includes("ownerOfCordaAsset") ? 30 : 20,
    fontFamily: "Roboto",
    color: highlighted.includes("ownerOfCordaAsset") ? "red" : "black",

    // backgroundColor: highlighted.includes("ownerOfCordaAsset") ? "yellow" : "transparent",
  });
  return (
    <Layout>
      <div style={{ minHeight: "100vh", paddingTop: 150, paddingLeft: 50 }}>
        <h1 style={{ fontFamily: "Roboto" }}>Harmonia</h1>
        <div style={{ width: "100%", display: "flex", paddingBottom: 100 }}>
          <Toaster />
          <div
            style={{
              width: 300,
              borderRight: "1px solid black",
              marginRight: 50,
            }}
          >
            <h3 style={{ fontFamily: "Roboto" }}>Alice</h3>

            {step == 2 && (
              <button
                onClick={() => {
                  toast.promise(commitWithTokenFlow(), {
                    loading: "Committing With Token",
                    success: "Committed With Token",
                    error: "Error Committing With Token",
                  });
                }}
              >
                Commit With Token Flow
              </button>
            )}
            {step == 4 && (
              <button
                onClick={() =>
                  toast.promise(claimCommitmentFlow(), {
                    loading: "Claiming Commitment",
                    success: "Claimed Commitment",
                    error: "Error Claiming Commitment",
                  })
                }
              >
                Claim Commitment Flow
              </button>
            )}

            {step == 5 && (
              <button
                onClick={() =>
                  toast.promise(RequestBlockProofs(), {
                    loading: "Requesting Block Proofs",
                    success: "Requested Block Proofs",
                    error: "Error Requesting Block Proofs",
                  })
                }
              >
                {" "}
                Request Block Proofs
              </button>
            )}
            {step == 6 && (
              <button
                onClick={() => {
                  toast.promise(unlockAsset(), {
                    loading: "Unlocking Asset",
                    success: "Unlocked Asset",
                    error: "Error Unlocking Asset",
                  });
                }}
              >
                Unlock Asset
              </button>
            )}
          </div>
          <div>
            <h3 style={{ fontFamily: "Roboto" }}>Bob</h3>

            {step == 0 && (
              <button
                onClick={() => {
                  toast.promise(issueGenericAsset(), {
                    loading: "Issuing Asset",
                    success: "Asset Issued",
                    error: "Error Issuing Asset",
                  });
                }}
              >
                Issue Asset
              </button>
            )}
            {step == 1 && (
              <button
                onClick={() =>
                  toast.promise(requestLockByEvent(), {
                    loading: "Requesting Lock By Event",
                    success: "Lock Requested",
                    error: "Error Requesting Lock",
                  })
                }
              >
                Request Lock By Event
              </button>
            )}
            {step == 3 && (
              <button
                onClick={() => {
                  toast.promise(signDraftTransactionById(), {
                    loading: "Signing Draft Transaction",
                    success: "Draft Transaction Signed",
                    error: "Error Signing Draft Transaction",
                  });
                }}
              >
                Sign Draft Transaction By Id
              </button>
            )}
          </div>
        </div>
        <div>
          <h4 style={{ fontFamily: "Roboto" }}>Balances (EVM):</h4>
          <ul>
            <li>
              Alice:{" "}
              <animated.span style={aliceEvmBalanceProps}>
                {evmBalanceAccount1}
              </animated.span>
            </li>

            <li>
              Bob:{" "}
              <animated.span style={bobEvmBalanceProps}>
                {evmBalanceAccount2}
              </animated.span>
            </li>
            <li>
              Swap Contract:
              <animated.span style={swapContractBalanceProps}>
                {" "}
                {swapContractBalance}
              </animated.span>
            </li>
          </ul>
        </div>

        <div>
          Owner of Corda Asset:
          <animated.h4 style={ownerOfCordaAssetProps}>
            <b>{owner}</b>
          </animated.h4>
        </div>

        <button onClick={reset}>Reset</button>
      </div>
    </Layout>
  );
};

export default Harmonia;
