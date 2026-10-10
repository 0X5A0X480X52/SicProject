"""Gateway regression for cross-department review, return/resubmit, stale review rounds and rejected acceptance."""
from __future__ import annotations
import argparse
import copy
import hashlib
import os
from pathlib import Path
from verify_business_lifecycle import LifecycleRunner, ROOT, require
from startup_common import read_env


class BranchRunner(LifecycleRunner):
    def __init__(self,args):
        super().__init__(args)
        self.report["caseId"]="E2E-BIZ-RETURN-REJECT-002"
        self.report["branchAssertions"]=[]
        self.historical_batches=[]

    def bootstrap(self):
        super().bootstrap()
        other=self.request("administrator","POST","/api/admin/departments",body={"deptCode":"OTHER_"+self.run_id,
            "deptName":"Other E2E department","parentDeptId":None,"enabled":True})
        actor="otherDepartment"
        username="e2e_"+self.run_id+"_otherdept"
        self.secrets.append(username)
        self.accounts[actor]={"username":username,"password":self.password}
        registered=self.request(actor,"POST","/api/auth/register",token="",body={"username":username,"password":self.password,
            "realName":"Other department admin","deptId":other["deptId"],"phone":"","email":"other@example.invalid"})
        self.accounts[actor]["userId"]=registered["user"]["userId"]
        self.request("administrator","PUT",f"/api/admin/users/{registered['user']['userId']}/roles",body={"roleCodes":["DEPT_ADMIN"]})
        self.login(actor)
        application=self.request("leader","POST","/api/expert-qualification/applications",body={"specialty":"Regression",
            "professionalTitle":"Researcher","applicationReason":"Cross department test "+self.run_id})
        self.request(actor,"POST",f"/api/admin/expert-qualification/applications/{application['applicationId']}/dept-review",
            body={"approved":True,"opinion":"Cross department attempt","remark":self.run_id},status=403)
        current=self.request("leader","GET","/api/expert-qualification/my")
        require(current["hasActiveApplication"] and not current["expert"],"Cross department rejection altered qualification")
        self.report["branchAssertions"].append("cross-department qualification review rejected")
        notice=self.request("science","POST","/api/notices",body={"moduleType":"APPLICATION","noticeType":"REGRESSION",
            "noticeTitle":"Notice "+self.run_id,"noticeScope":"DEPT","targetDeptScope":str(self.dept_id)})
        require(notice["moduleInstanceId"] is None and notice["publishTime"] is None,"Optional notice fields changed")
        self.request(actor,"GET",f"/api/notices/{notice['noticeId']}",status=404)
        updated=self.request("science","PUT",f"/api/notices/{notice['noticeId']}",body={"moduleType":"APPLICATION",
            "noticeType":"REGRESSION","noticeTitle":"Updated "+self.run_id,"noticeScope":"DEPT","targetDeptScope":str(self.dept_id)})
        require(updated["noticeTitle"]=="Updated "+self.run_id,"Notice update was not persisted")
        self.report["fixtures"]["noticeId"]=notice["noticeId"]

    def traverse(self):
        application=self.case["modules"][0]
        module=self.modules["APPLICATION"]
        steps=application["steps"]
        self.check("initial application submission",lambda:self.step(module,steps[0]))
        returned=copy.deepcopy(steps[1])
        returned.update(result="RETURNED",nextNodeId="SubmitApplicationTask",nextState="APPLICATION_DRAFT")
        self.check("department return",lambda:self.step(module,returned))
        require(self.runtime(module)["context"]["currentRoundNo"]==2,"Department return did not open round two")
        self.check("application resubmission",lambda:self.step(module,steps[0]))
        science_index=next(i for i,s in enumerate(steps) if s["nodeId"]=="ScienceOfficeInitialReviewTask")
        for step in steps[1:science_index]:self.check("round two "+step["nodeId"],lambda s=step:self.step(module,s))
        old=self.batches[f"{module}:DeptExpertReviewTask"]
        self.historical_batches.append(old["batch"]["batchId"])
        assignment=next(a["assignment"]["assignmentId"] for a in old["assignments"]
            if a["assignment"]["expertUserId"]==self.accounts["expert1"]["userId"])
        returned=copy.deepcopy(steps[science_index])
        returned.update(result="RETURNED",nextNodeId="SubmitApplicationTask",nextState="APPLICATION_DRAFT")
        self.check("science return after expert round",lambda:self.step(module,returned))
        require(self.runtime(module)["context"]["currentRoundNo"]==3,"Science return did not open round three")
        self.request("expert1","POST",f"/api/expert-reviews/assignments/{assignment}/scores",body={"conflictOfInterest":False,"valid":True,
            "reviewResult":"APPROVED","reviewComment":"Stale round","scores":[{"itemCode":"TOTAL","itemName":"Total","scoreValue":100}]},status=409)
        self.report["branchAssertions"].extend(["department return and resubmission","science return and resubmission","old round score rejected"])
        for step in steps:self.check("round three "+step["nodeId"],lambda s=step:self.step(module,s))
        for specification in self.case["modules"][1:]:
            kind=specification["moduleType"]
            module=self.successor(kind);self.modules[kind]=module
            for original in specification["steps"]:
                step=copy.deepcopy(original)
                if kind=="ACCEPTANCE" and step["nodeId"]=="IssueCertificateTask":break
                if kind=="ACCEPTANCE" and step["nodeId"]=="ExpertSummaryTask":
                    step.update(nextNodeId="IssueFailFileTask",nextState="ACCEPTANCE_FAIL_FILE_ISSUING")
                self.check(kind+"/"+step["nodeId"],lambda s=step:self.step(module,s))
            if kind=="ACCEPTANCE":
                self.check("acceptance failure document",lambda:self.dynamic_step(module,"science","ACCEPTANCE_FAIL_FILE_ISSUED","FAIL_FILE_ISSUED","ReturnSurplusFundsTask"))
                self.check("surplus funds return",lambda:self.dynamic_step(module,"finance","SURPLUS_FUNDS_RETURNED","PROCESS_REJECTED","RejectedEndEvent"))
            final=self.runtime(module)
            expected="ACCEPTANCE_REJECTED" if kind=="ACCEPTANCE" else specification["finalState"]
            require(final["context"]["currentState"]==expected and not final["openTasks"],"Branch module did not finish")
        self.report["workflowTraversalPassed"]=True

    def score_batch(self,module,step):
        if self.runtime(module)["context"]["moduleType"]!="ACCEPTANCE":return super().score_batch(module,step)
        batch=self.batches[f"{module}:{step['nodeId']}"]
        assignments={a["assignment"]["expertUserId"]:a["assignment"]["assignmentId"] for a in batch["assignments"]}
        for index,expert in enumerate(("expert1","expert2")):
            batch=self.request(expert,"POST",f"/api/expert-reviews/assignments/{assignments[self.accounts[expert]['userId']]}/scores",
                body={"conflictOfInterest":False,"valid":True,"reviewResult":"REJECTED","reviewComment":"Reject fixture",
                    "scores":[{"itemCode":"OVERALL","itemName":"Total","weight":1,"maxScore":100,"scoreValue":30+index*10,"comment":"E2E"}]})
            if index==0:require(batch["batch"]["status"]!="COMPLETED" and self.runtime(module)["context"]["currentNodeId"]==step["nodeId"],"One rejection completed the batch")
        require(batch["batch"]["submittedExpertCount"]==2 and float(batch["batch"]["finalScore"])==35 and batch["batch"]["finalResult"]=="REJECTED","Rejected review aggregate differs")
        self.poll("rejected review automatic advancement",lambda:self.runtime(module),lambda v:v["context"]["currentNodeId"]==step["nextNodeId"])

    def dynamic_step(self,module,actor,event,result,next_node):
        view=self.runtime(module,actor)
        form=view["nodeForms"][0]
        codes=[r["materialTypeCode"] for r in view["materialRequirements"] if r["required"]]
        step={"nodeId":view["context"]["currentNodeId"],"state":view["context"]["currentState"],"actor":actor,"action":"TRANSITION",
            "eventType":event,"result":result,"form":form,"requiredMaterials":codes,"nextNodeId":next_node}
        self.step(module,step)

    def final_assertions(self):
        self.poll("rejected project lifecycle",lambda:self.request("science","GET",f"/api/projects/{self.project}/authorization"),
            lambda p:p["project"]["lifecycleStage"]=="ACCEPTANCE_REJECTED")
        for module in self.modules.values():
            view=self.runtime(module)
            require(not view["openTasks"],"Rejected branch left an open task")
            sequence=[row["seq"] for row in view["history"]]
            require(sequence==list(range(1,len(sequence)+1)),"Branch state sequence has a gap")
        digest=hashlib.sha256(self.args.fixture_pdf.read_bytes()).hexdigest()
        for version in self.materials.values():
            require(hashlib.sha256(self.request("science","GET",f"/api/material-versions/{version}/download",raw=True)).hexdigest()==digest,"Branch upload download differs")
        self.report["fixtures"].update(historicalBatchIds=self.historical_batches,expectedFinalLifecycleStage="ACCEPTANCE_REJECTED")
        self.report["branchAssertions"].append("acceptance rejection and surplus return completed")
        self.report["businessDataPassed"]=True


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway-base-url",default="http://127.0.0.1:18080")
    parser.add_argument("--env-file",required=True,type=Path)
    parser.add_argument("--case",type=Path,default=ROOT/"verification/scenarios/full-business-lifecycle.case.json")
    parser.add_argument("--fixture-pdf",type=Path,default=ROOT/"output/pdf/blank-upload.pdf")
    parser.add_argument("--report-path",type=Path)
    parser.add_argument("--request-timeout",type=float,default=15)
    parser.add_argument("--poll-timeout",type=float,default=30)
    parser.add_argument("--poll-interval",type=float,default=.5)
    parser.add_argument("--request-interval",type=float,default=.05)
    parser.add_argument("--negative-cases",action="store_true")
    args=parser.parse_args()
    credentials=read_env(args.env_file)
    for name in ("USERNAME","PASSWORD"):os.environ.setdefault("SIC_E2E_ADMIN_"+name,credentials["BOOTSTRAP_ADMIN_"+name])
    return BranchRunner(args).execute()


if __name__=="__main__":raise SystemExit(main())
